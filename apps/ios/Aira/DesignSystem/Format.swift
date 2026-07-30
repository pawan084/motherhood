import Foundation

func journeyLabel(_ j: String?) -> String {
    switch j {
    case "trying": return "Trying to conceive"
    case "pregnant": return "Pregnant"
    case "postpartum": return "Postpartum"
    default: return "Exploring"
    }
}

/// The concrete verb for a next-step, per tool — never a generic "Confirm".
func actionPrimaryLabel(_ tool: String) -> String {
    switch tool {
    case "checkin": return "Start check-in"
    case "reminder": return "Set a reminder"
    case "medicine": return "Add medicine"
    case "appointment": return "Prepare for it"
    case "wellness": return "Take two minutes"
    case "symptom": return "Log it"
    case "careplan": return "See the plan"
    default: return "Start"
    }
}

func videoDurationLabel(_ v: VideoTopic) -> String {
    let lo = max(1, Int((Double(v.duration.minSeconds) / 60).rounded()))
    let hi = max(lo, Int((Double(v.duration.maxSeconds) / 60).rounded()))
    return lo == hi ? "\(lo) min" : "\(lo)–\(hi) min"
}

func timeGreeting() -> String {
    let h = Calendar.current.component(.hour, from: Date())
    if h < 12 { return "Good morning" }
    if h < 18 { return "Good afternoon" }
    return "Good evening"
}
