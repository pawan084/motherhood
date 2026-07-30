import SwiftUI

/// First-run gating, mirroring the other clients: resolve the session, then show
/// onboarding for a new user or the tabbed app for a returning one. The urgent
/// handoff can appear over anything.
struct RootView: View {
    @EnvironmentObject var state: AppState

    var body: some View {
        Group {
            if state.booting {
                BootView()
            } else if !state.onboarded {
                OnboardingView()
            } else {
                MainTabView()
            }
        }
        .task { await state.bootstrap() }
        .sheet(item: $state.urgent) { help in
            UrgentView(help: help)
        }
    }
}

private struct BootView: View {
    var body: some View {
        ZStack {
            Color.airaIvory.ignoresSafeArea()
            ProgressView().tint(.airaAubergine)
        }
    }
}
