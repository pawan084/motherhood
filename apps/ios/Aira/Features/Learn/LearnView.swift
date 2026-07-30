import SwiftUI

/// Learn — the educational video library, served by GET /v1/videos (the server
/// resolves journey + gestational week). No media yet, so playback shows an
/// honest "in production" state; urgent topics route to care, not reassurance.
struct LearnView: View {
    @EnvironmentObject var state: AppState
    @State private var category = "all"
    @State private var selected: VideoTopic?

    var body: some View {
        ScreenScaffold(
            title: "Short guided videos",
            subtitle: "Clinician-reviewed topics for where you are — in production now. Save any for when they're ready."
        ) {
            if let wv = state.videos?.weekVideo { featured(wv) }
            if let cats = state.videos?.categories, !cats.isEmpty { chips(cats) }

            let items = (state.videos?.items ?? []).filter { category == "all" || $0.category == category }
            if items.isEmpty {
                Text("No topics yet.").font(.system(size: 14)).foregroundColor(.airaMuted)
            }
            ForEach(items) { v in videoCard(v) }

            Text("Every video is clinician-reviewed before it's published. Aira is wellness support, not diagnosis or emergency care.")
                .font(.system(size: 13)).foregroundColor(.airaMuted).padding(.top, 4)
        }
        .task { if state.videos == nil { await state.refresh() } }
        .sheet(item: $selected) { v in VideoDetailSheet(video: v) }
    }

    @ViewBuilder private func featured(_ wv: VideoTopic) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Pill(text: "✨ Your week with Aira", bg: Color.white.opacity(0.14), fg: Color(hex: 0xF3E9F4))
            Text(wv.title).font(.airaSerif(24)).foregroundColor(.white)
            Text("\(wv.description) · \(videoDurationLabel(wv))")
                .font(.system(size: 14)).foregroundColor(Color(hex: 0xECDFED))
            HStack {
                Button { selected = wv } label: {
                    HStack(spacing: 6) { Image(systemName: "play.fill"); Text("Preview") }
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.vertical, 10).padding(.horizontal, 16)
                        .background(Color.airaPaper).foregroundColor(.airaAubergine)
                        .clipShape(RoundedRectangle(cornerRadius: 11))
                }
                Button { Task { await state.toggleSaveVideo(wv.id) } } label: {
                    Text(state.savedVideoIds.contains(wv.id) ? "Saved" : "Save")
                        .font(.system(size: 14, weight: .semibold)).foregroundColor(Color(hex: 0xF3E9F4))
                        .padding(.vertical, 10).padding(.horizontal, 14)
                        .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.white.opacity(0.25)))
                }
            }.padding(.top, 4)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Color(hex: 0x4D2052), Color(hex: 0x723779)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    @ViewBuilder private func chips(_ cats: [VideoCategory]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip("All", "all")
                ForEach(cats) { c in chip(c.label, c.key) }
            }
        }
    }

    @ViewBuilder private func chip(_ label: String, _ key: String) -> some View {
        let isOn = category == key
        Button { category = key } label: {
            Text(label).font(.system(size: 13, weight: .semibold))
                .padding(.vertical, 8).padding(.horizontal, 13)
                .background(isOn ? Color.airaAubergine : Color.airaPaper)
                .foregroundColor(isOn ? .airaPaper : .airaMuted)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(isOn ? Color.airaAubergine : Color.airaLine))
        }
    }

    @ViewBuilder private func videoCard(_ v: VideoTopic) -> some View {
        AiraCard {
            VStack(alignment: .leading, spacing: 7) {
                HStack {
                    Pill(text: v.categoryLabel)
                    if v.safetyLevel == "urgent" {
                        Pill(text: "Urgent · see your care team", bg: Color.airaUrgentMist, fg: .airaUrgent)
                    }
                    Spacer()
                    Text(videoDurationLabel(v)).font(.system(size: 12, weight: .medium)).foregroundColor(.airaMuted)
                }
                Text(v.title).font(.system(size: 16, weight: .medium)).foregroundColor(.airaInk)
                Text(v.description).font(.system(size: 13)).foregroundColor(.airaMuted)
                HStack {
                    Spacer()
                    Button { Task { await state.toggleSaveVideo(v.id) } } label: {
                        HStack(spacing: 5) {
                            Image(systemName: state.savedVideoIds.contains(v.id) ? "bookmark.fill" : "bookmark")
                            Text(state.savedVideoIds.contains(v.id) ? "Saved" : "Save")
                        }.font(.system(size: 12, weight: .semibold)).foregroundColor(.airaAubergine)
                    }.buttonStyle(.plain)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { selected = v }
    }
}

/// The detail: an honest "in production" stage, and for urgent topics a route to
/// care rather than a playable video.
struct VideoDetailSheet: View {
    let video: VideoTopic
    @EnvironmentObject var state: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(spacing: 10) {
                        Image(systemName: "play.circle").font(.system(size: 34)).foregroundColor(Color(hex: 0xEFE4F0))
                        Text("In production — this video isn't ready to play yet.")
                            .font(.system(size: 13)).foregroundColor(Color(hex: 0xE3D3E4))
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity).padding(28)
                    .background(
                        LinearGradient(colors: [Color(hex: 0x4D2052), Color(hex: 0x723779)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    if video.safetyLevel == "urgent" {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("This one is about knowing when to get help — not something to watch and wait on.")
                                .font(.system(size: 15, weight: .semibold)).foregroundColor(.airaUrgent)
                            Text("If you're experiencing this now, contact your care team or your local emergency number. Aira won't try to reassure you through it.")
                                .font(.system(size: 13)).foregroundColor(Color(hex: 0x7C4A46))
                            Button {
                                state.urgent = UrgentHelp(
                                    headline: "Get help now",
                                    message: "Contact your care team or your local emergency number.",
                                    careTeam: nil, emergencyContact: nil, showEmergencyServices: true)
                                dismiss()
                            } label: {
                                HStack(spacing: 6) { Image(systemName: "cross.case.fill"); Text("Get urgent help") }
                                    .font(.system(size: 14, weight: .semibold)).foregroundColor(.airaPaper)
                                    .padding(.vertical, 11).frame(maxWidth: .infinity)
                                    .background(Color.airaUrgent).clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }
                        .padding(16).background(Color.airaUrgentMist).clipShape(RoundedRectangle(cornerRadius: 14))
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            SectionLabel(text: "What it covers", color: .airaAubergine)
                            Text(video.description).font(.system(size: 15)).foregroundColor(.airaInk)
                        }
                        GhostButton(label: state.savedVideoIds.contains(video.id) ? "Saved" : "Save for later") {
                            Task { await state.toggleSaveVideo(video.id) }
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.airaIvory.ignoresSafeArea())
            .navigationTitle(video.categoryLabel)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
        }
    }
}
