import Foundation

func greeting(for date: Date = Date()) -> String {
    let hour = Calendar.current.component(.hour, from: date)
    switch hour {
    case 5..<12: return "Good morning"
    case 12..<17: return "Good afternoon"
    case 17..<22: return "Good evening"
    default: return "Still up"
    }
}

private let longDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateStyle = .full
    formatter.timeStyle = .none
    return formatter
}()

private let compactDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = "MMM d"
    return formatter
}()

private let activityTimeFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return formatter
}()

func todayLabel(_ date: Date = Date()) -> String {
    longDateFormatter.string(from: date)
}

func compactDateLabel(_ value: String?) -> String {
    guard let date = parseDateOnly(value) else { return "No date" }
    return compactDateFormatter.string(from: date)
}

func activityTimeLabel(_ value: String?) -> String {
    guard let date = parseTimestamp(value) else { return "" }
    if Calendar.current.isDateInToday(date) { return activityTimeFormatter.string(from: date) }
    return relativeTime(value)
}

func prioritySummary(_ priority: Priority) -> String {
    "\(priority.label) priority"
}
