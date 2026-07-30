import SwiftUI

/// The care tools that have an add-form. Shared by Today's next-step and Care's
/// quick actions so both open the same sheet.
enum CareKind: String, Identifiable {
    case checkin, reminder, medicine, appointment, symptom
    var id: String { rawValue }

    var title: String {
        switch self {
        case .checkin: return "Daily check-in"
        case .reminder: return "New reminder"
        case .medicine: return "Add a medicine"
        case .appointment: return "Add an appointment"
        case .symptom: return "Log a symptom"
        }
    }

    /// Maps a backend action_card / next_action tool to a form, when one exists.
    static func from(tool: String) -> CareKind? { CareKind(rawValue: tool) }
}

struct AddCareSheet: View {
    let kind: CareKind
    @EnvironmentObject var state: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var a = ""
    @State private var b = ""
    @State private var c = ""

    var body: some View {
        NavigationStack {
            Form {
                switch kind {
                case .reminder:
                    TextField("What to remember", text: $a)
                    TextField("Time (e.g. 9:00 AM)", text: $b)
                    TextField("Repeat (e.g. Daily)", text: $c)
                case .medicine:
                    TextField("Medicine name", text: $a)
                    TextField("Dose (e.g. 1 tablet)", text: $b)
                    TextField("Time (e.g. 9:00 AM)", text: $c)
                case .appointment:
                    TextField("Who it's with", text: $a)
                    TextField("Where", text: $b)
                    TextField("When, in your words", text: $c)
                case .checkin:
                    TextField("How are you feeling?", text: $a)
                    TextField("Hours of sleep", text: $b).keyboardType(.decimalPad)
                    TextField("Anything to note", text: $c)
                case .symptom:
                    TextField("What did you notice?", text: $a)
                    TextField("How strong (mild / moderate / …)", text: $b)
                    TextField("When it started", text: $c)
                }
            }
            .navigationTitle(kind.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save(); dismiss() } }
                        .disabled(kind != .checkin && a.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func save() async {
        let clean: (String) -> String? = {
            let t = $0.trimmingCharacters(in: .whitespaces)
            return t.isEmpty ? nil : t
        }
        switch kind {
        case .reminder:    await state.addReminder(title: a, time: clean(b), repeatText: clean(c))
        case .medicine:    await state.addMedicine(name: a, dose: clean(b), schedule: nil, time: clean(c))
        case .appointment: await state.addAppointment(doctor: a, place: clean(b), when: clean(c))
        case .checkin:     await state.addCheckin(feeling: clean(a), sleepHours: Double(b), note: clean(c))
        case .symptom:     await state.addSymptom(what: a, severity: clean(b), started: clean(c))
        }
    }
}
