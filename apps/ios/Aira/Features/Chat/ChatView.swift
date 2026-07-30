import SwiftUI

/// Aira — the safety-gated conversation. Every message is screened server-side
/// (POST /v1/chat/turn); a red result opens the urgent handoff rather than a reply.
struct ChatView: View {
    @EnvironmentObject var state: AppState
    @State private var draft = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Aira").font(.airaSerif(20)).foregroundColor(.airaInk)
                    Text(state.degraded ? "Keyword-only screening" : "Every message is screened")
                        .font(.system(size: 12)).foregroundColor(.airaMuted)
                }
                Spacer()
            }
            .padding(16)

            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        if state.messages.isEmpty {
                            Text("Tell Aira what's on your mind. Every message is screened before Aira replies.")
                                .font(.system(size: 14)).foregroundColor(.airaMuted).padding(.top, 20)
                        }
                        ForEach(state.messages) { m in bubble(m).id(m.id) }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: state.messages.count) { _ in
                    if let last = state.messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            HStack(spacing: 8) {
                TextField("Message Aira", text: $draft, axis: .vertical)
                    .padding(11)
                    .background(Color.airaPaper)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.airaLine))
                Button { send() } label: {
                    Image(systemName: "arrow.up").font(.system(size: 16, weight: .bold))
                        .foregroundColor(.airaPaper).frame(width: 40, height: 40)
                        .background(Color.airaAubergine).clipShape(Circle())
                }
                .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(12)
        }
        .background(Color.airaIvory.ignoresSafeArea())
    }

    private func send() {
        let text = draft
        draft = ""
        Task { await state.sendMessage(text) }
    }

    @ViewBuilder private func bubble(_ m: ChatMessage) -> some View {
        HStack {
            if m.role == .me { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 4) {
                if let tl = m.trustLabel {
                    SectionLabel(text: tl == "wellness" ? "Wellness" : (tl == "watchful" ? "Watchful" : tl),
                                 color: .airaSageDeep)
                }
                Text(m.text).font(.system(size: 15)).foregroundColor(m.role == .me ? .airaPaper : .airaInk)
                if let card = m.actionCard {
                    Text("→ \(card.title)").font(.system(size: 12, weight: .semibold))
                        .foregroundColor(m.role == .me ? .airaPaper : .airaAubergine)
                }
            }
            .padding(12)
            .background(m.role == .me ? Color.airaAubergine : Color.airaPaper)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay {
                if m.role == .aira { RoundedRectangle(cornerRadius: 14).stroke(Color.airaLine) }
            }
            if m.role == .aira { Spacer(minLength: 40) }
        }
    }
}
