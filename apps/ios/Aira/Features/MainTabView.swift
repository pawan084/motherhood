import SwiftUI

/// The six sections, matching web/Android: Today, Aira, Journey, Learn, Care, You.
/// iOS folds anything past the fifth tab into an automatic "More" list.
struct MainTabView: View {
    @EnvironmentObject var state: AppState

    var body: some View {
        TabView {
            TodayView().tabItem { Label("Today", systemImage: "house.fill") }
            ChatView().tabItem { Label("Aira", systemImage: "sparkles") }
            JourneyView().tabItem { Label("Journey", systemImage: "book.fill") }
            LearnView().tabItem { Label("Learn", systemImage: "play.circle.fill") }
            CareView().tabItem { Label("Care", systemImage: "cross.case.fill") }
            YouView().tabItem { Label("You", systemImage: "person.fill") }
        }
        .overlay(alignment: .bottom) {
            if let toast = state.toast {
                Text(toast)
                    .font(.system(size: 14, weight: .medium))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Color.airaInk)
                    .foregroundColor(.airaPaper)
                    .clipShape(Capsule())
                    .padding(.bottom, 64)
                    .shadow(radius: 8, y: 2)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: toast) {
                        try? await Task.sleep(nanoseconds: 2_200_000_000)
                        state.toast = nil
                    }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: state.toast)
    }
}
