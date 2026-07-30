import SwiftUI

/// You — profile, the consent toggles Aira enforces, and data rights.
struct YouView: View {
    @EnvironmentObject var state: AppState
    @State private var confirmDelete = false

    var body: some View {
        ScreenScaffold(title: "You", subtitle: "Profile, privacy and your data.") {
            AiraCard {
                VStack(alignment: .leading, spacing: 6) {
                    SectionLabel(text: "Profile")
                    Text((state.user?.name.isEmpty == false) ? state.user!.name : "You")
                        .font(.system(size: 17, weight: .medium)).foregroundColor(.airaInk)
                    Text([journeyLabel(state.user?.journey), state.user?.language].compactMap { $0 }.joined(separator: " · "))
                        .font(.system(size: 13)).foregroundColor(.airaMuted)
                    Text(state.user?.email ?? "Private account")
                        .font(.system(size: 13)).foregroundColor(.airaMuted)
                }
            }

            AiraCard {
                VStack(alignment: .leading, spacing: 10) {
                    SectionLabel(text: "What Aira may do")
                    if state.consent.isEmpty {
                        Text("Loading your settings…").font(.system(size: 13)).foregroundColor(.airaMuted)
                    }
                    ForEach(state.consent) { f in
                        Toggle(isOn: Binding(
                            get: { f.granted },
                            set: { v in Task { await state.setConsent(f.key, granted: v) } }
                        )) {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(f.label).font(.system(size: 15)).foregroundColor(.airaInk)
                                if f.locked {
                                    Text("Locked policy").font(.system(size: 11)).foregroundColor(.airaMuted)
                                } else if !f.available {
                                    Text("Not available in this build").font(.system(size: 11)).foregroundColor(.airaMuted)
                                }
                            }
                        }
                        .tint(.airaAubergine)
                        .disabled(f.locked || !f.available)
                    }
                }
            }

            AiraCard(background: .airaSageSoft) {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "lock.fill").foregroundColor(.airaSageDeep)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Never used for advertising")
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(.airaSageDeep)
                        Text("You control what Aira remembers. Review or delete it any time.")
                            .font(.system(size: 11)).foregroundColor(.airaSageDeep)
                    }
                }
            }

            VStack(spacing: 8) {
                GhostButton(label: "Sign out") { state.signOut() }
                Button(role: .destructive) { confirmDelete = true } label: {
                    Text("Delete my account")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                }
            }
        }
        .task { if state.consent.isEmpty { await state.loadConsent() } }
        .alert("Delete everything?", isPresented: $confirmDelete) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) { Task { await state.deleteAccount() } }
        } message: {
            Text("This permanently erases your account and all care data. It can't be undone.")
        }
    }
}
