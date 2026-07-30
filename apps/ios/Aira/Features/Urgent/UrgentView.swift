import SwiftUI

/// The urgent handoff — a red result routes here instead of an AI answer. Offers
/// real tap-to-call actions from the emergency profile; no reassurance.
struct UrgentView: View {
    let help: UrgentHelp
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                Image(systemName: "cross.case.fill").foregroundColor(.airaUrgent).font(.system(size: 22))
                Text(help.headline).font(.airaSerif(24)).foregroundColor(.airaInk)
                Spacer()
                Button { dismiss() } label: { Image(systemName: "xmark").foregroundColor(.airaMuted) }
            }

            Text(help.message).font(.system(size: 15)).foregroundColor(.airaInk)

            if let phone = help.careTeam?.phone {
                callRow(title: "Call your care team", subtitle: help.careTeam?.name ?? phone, phone: phone)
            }
            if let phone = help.emergencyContact?.phone {
                callRow(title: "Call \(help.emergencyContact?.name ?? "your emergency contact")",
                        subtitle: phone, phone: phone)
            }
            if help.showEmergencyServices == true {
                Text("If this is an emergency, call your local emergency number now.")
                    .font(.system(size: 13, weight: .semibold)).foregroundColor(.airaUrgent)
            }

            Spacer()
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.airaIvory.ignoresSafeArea())
    }

    @ViewBuilder private func callRow(title: String, subtitle: String, phone: String) -> some View {
        let dialable = phone.filter { $0.isNumber || $0 == "+" }
        let content = HStack {
            Image(systemName: "phone.fill").foregroundColor(.airaPaper)
                .frame(width: 40, height: 40).background(Color.airaUrgent).clipShape(Circle())
            VStack(alignment: .leading) {
                Text(title).font(.system(size: 15, weight: .semibold)).foregroundColor(.airaInk)
                Text(subtitle).font(.system(size: 13)).foregroundColor(.airaMuted)
            }
            Spacer()
        }
        .padding(12)
        .background(Color.airaPaper)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.airaLine))

        if let url = URL(string: "tel://\(dialable)") {
            Link(destination: url) { content }
        } else {
            content
        }
    }
}
