import Foundation

enum ProgressTone: String {
    case success, accent, warning, danger
}

// MARK: - Goal progress (same weights as apps/web/src/lib/progress.ts)

func goalPercent(_ goal: Goal) -> Int {
    if goal.status == .completed { return 100 }

    let allTasks = goal.tasks
    let allMilestones = goal.milestones
    let taskPercent = allTasks.isEmpty ? 0 : Int(round(Double(allTasks.filter(\.completed).count) / Double(allTasks.count) * 100))
    let milestonePercent = allMilestones.isEmpty ? 0 : Int(round(Double(allMilestones.filter(\.completed).count) / Double(allMilestones.count) * 100))

    if !allTasks.isEmpty && !allMilestones.isEmpty {
        return Int(round(Double(taskPercent) * 0.65 + Double(milestonePercent) * 0.35))
    }
    if !allTasks.isEmpty { return taskPercent }
    if !allMilestones.isEmpty { return milestonePercent }
    return 0
}

func progressTone(_ percent: Int) -> ProgressTone {
    if percent >= 100 { return .success }
    if percent >= 60 { return .accent }
    if percent >= 25 { return .warning }
    return .danger
}

func rootTasks(in goal: Goal) -> [GoalTask] {
    goal.tasks
        .filter { $0.parentTaskId == nil }
        .sorted { taskOrder($0, $1) }
}

func subtasks(in goal: Goal, for parentID: String) -> [GoalTask] {
    goal.tasks
        .filter { $0.parentTaskId == parentID }
        .sorted { taskOrder($0, $1) }
}

private func taskOrder(_ lhs: GoalTask, _ rhs: GoalTask) -> Bool {
    if lhs.order != rhs.order { return lhs.order < rhs.order }
    return lhs.createdAt < rhs.createdAt
}

func nextActionableTask(in goal: Goal) -> GoalTask? {
    goal.tasks
        .filter { !$0.completed }
        .sorted { lhs, rhs in
            let left = lhs.dueDate.flatMap(parseDateOnly) ?? Date.distantFuture
            let right = rhs.dueDate.flatMap(parseDateOnly) ?? Date.distantFuture
            if left != right { return left < right }
            if lhs.order != rhs.order { return lhs.order < rhs.order }
            return lhs.createdAt < rhs.createdAt
        }
        .first
}

func overdueTasks(in goal: Goal, now: Date = Date()) -> [GoalTask] {
    let today = Calendar.current.startOfDay(for: now)
    return goal.tasks.filter { task in
        guard !task.completed, let due = task.dueDate.flatMap(parseDateOnly) else { return false }
        return due < today
    }
}

func tasksDueThisWeek(_ goals: [Goal], weekStartsOn: Int = 1, now: Date = Date()) -> [GoalTask] {
    let start = startOfWeek(now, weekStartsOn: weekStartsOn)
    guard let end = Calendar.current.date(byAdding: .day, value: 7, to: start) else { return [] }
    return goals
        .filter(\.isActive)
        .flatMap(\.tasks)
        .filter { task in
            guard !task.completed, let due = task.dueDate.flatMap(parseDateOnly) else { return false }
            return due >= start && due < end
        }
        .sorted { ($0.dueDate ?? "") < ($1.dueDate ?? "") }
}

// Top focus queue: overdue first via due date, then priority critical -> low.
func focusTasks(_ goals: [Goal], limit: Int = 5, now: Date = Date()) -> [GoalTask] {
    let today = Calendar.current.startOfDay(for: now)
    return Array(goals
        .filter(\.isActive)
        .flatMap(\.tasks)
        .filter { !$0.completed }
        .sorted { lhs, rhs in
            let leftDue = lhs.dueDate.flatMap(parseDateOnly)
            let rightDue = rhs.dueDate.flatMap(parseDateOnly)
            let leftOverdue = leftDue.map { $0 < today } ?? false
            let rightOverdue = rightDue.map { $0 < today } ?? false
            if leftOverdue != rightOverdue { return leftOverdue }
            if leftDue != rightDue { return (leftDue ?? Date.distantFuture) < (rightDue ?? Date.distantFuture) }
            if lhs.priority.rank != rhs.priority.rank { return lhs.priority.rank < rhs.priority.rank }
            return lhs.order < rhs.order
        }
        .prefix(limit))
}

// MARK: - Momentum signal

func momentumScore(_ goals: [Goal], now: Date = Date()) -> Int {
    let active = goals.filter(\.isActive)
    guard !active.isEmpty else { return 0 }

    let sevenDaysAgo = Calendar.current.date(byAdding: .day, value: -7, to: Calendar.current.startOfDay(for: now)) ?? now
    let completedRecently = active.flatMap(\.tasks).filter { task in
        guard task.completed, let date = task.completedAt.flatMap(parseTimestamp) else { return false }
        return date >= sevenDaysAgo
    }.count

    let available = active.reduce(0) { $0 + $1.tasks.count + $1.milestones.count }
    let progressAverage = Double(active.reduce(0) { $0 + goalPercent($1) }) / Double(active.count)
    let velocityBase = max(6, Double(available) * 0.25)
    let velocity = min(100, Int(round(Double(completedRecently) / velocityBase * 100)))

    return Int(round(progressAverage * 0.72 + Double(velocity) * 0.28))
}

func momentumCaption(_ score: Int) -> String {
    if score >= 75 { return "Strong cadence — keep the streak alive." }
    if score >= 45 { return "Momentum is compounding." }
    if score >= 20 { return "Signals are forming. Keep moving." }
    return "A quiet start. One task changes everything."
}

func completionSeries(_ goals: [Goal], days: Int = 7, now: Date = Date()) -> [(label: String, count: Int)] {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: now)
    let completions = goals.flatMap(\.tasks).compactMap { task -> Date? in
        guard task.completed else { return nil }
        return task.completedAt.flatMap(parseTimestamp).map { calendar.startOfDay(for: $0) }
    }
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "EEE"

    return (0..<days).reversed().compactMap { offset in
        guard let date = calendar.date(byAdding: .day, value: -offset, to: today) else { return nil }
        let key = dateOnlyString(date)
        return (formatter.string(from: date), completions.filter { dateOnlyString($0) == key }.count)
    }
}

func completedTodayCount(_ goals: [Goal], now: Date = Date()) -> Int {
    let key = dateOnlyString(now)
    return goals.flatMap(\.tasks).filter { task in
        guard task.completed, let date = task.completedAt.flatMap(parseTimestamp) else { return false }
        return dateOnlyString(date) == key
    }.count
}

// MARK: - Calendar helpers

private let dateOnlyFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = TimeZone.current
    return formatter
}()

private let shortDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    return formatter
}()

private let timestampFormatters: [ISO8601DateFormatter] = {
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let plain = ISO8601DateFormatter()
    plain.formatOptions = [.withInternetDateTime]
    return [fractional, plain]
}()

func isoString(_ date: Date = Date()) -> String {
    timestampFormatters[1].string(from: date)
}

func dateOnlyString(_ date: Date) -> String {
    dateOnlyFormatter.string(from: date)
}

func parseDateOnly(_ value: String?) -> Date? {
    guard let value, !value.isEmpty else { return nil }
    return dateOnlyFormatter.date(from: value)
}

func parseTimestamp(_ value: String?) -> Date? {
    guard let value, !value.isEmpty else { return nil }
    for formatter in timestampFormatters {
        if let date = formatter.date(from: value) { return date }
    }
    return nil
}

func startOfWeek(_ date: Date, weekStartsOn: Int = 1) -> Date {
    let calendar = Calendar.current
    let midnight = calendar.startOfDay(for: date)
    let weekday = calendar.component(.weekday, from: midnight) // Sunday = 1
    let mondayIndex = (weekday + 5) % 7
    let reference = weekStartsOn == 0 ? (weekday - 1) : mondayIndex
    return calendar.date(byAdding: .day, value: -reference, to: midnight) ?? midnight
}

func calendarDays(from start: Date, to end: Date) -> Int {
    let calendar = Calendar.current
    let from = calendar.dateComponents([.year, .month, .day], from: calendar.startOfDay(for: start))
    let to = calendar.dateComponents([.year, .month, .day], from: calendar.startOfDay(for: end))
    return calendar.dateComponents([.day], from: from, to: to).day ?? 0
}

func deadlineLabel(_ value: String?, now: Date = Date()) -> String? {
    guard let date = parseDateOnly(value) else { return nil }
    let days = calendarDays(from: now, to: date)
    if days < -1 { return "\(abs(days)) days overdue" }
    if days == -1 { return "1 day overdue" }
    if days == 0 { return "Due today" }
    if days == 1 { return "Due tomorrow" }
    return "Due in \(days) days"
}

func shortDate(_ value: String?) -> String? {
    guard let date = parseDateOnly(value) else { return nil }
    return shortDateFormatter.string(from: date)
}

func relativeTime(_ value: String?, now: Date = Date()) -> String {
    guard let date = parseTimestamp(value) else { return "" }
    let seconds = max(0, Int(now.timeIntervalSince(date)))
    if seconds < 60 { return "just now" }
    if seconds < 3600 { return "\(seconds / 60)m ago" }
    if seconds < 86_400 { return "\(seconds / 3600)h ago" }
    if seconds < 604_800 { return "\(seconds / 86_400)d ago" }
    return shortDateFormatter.string(from: date)
}

// MARK: - Recurrence

func recurrenceLabel(_ goal: Goal) -> String {
    guard goal.kind == .recurring, let recurrence = goal.recurrence else { return "One-time" }
    return recurrence.label
}

func nextOccurrence(from goal: Goal, now: Date = Date()) -> Goal? {
    guard goal.kind == .recurring else { return nil }
    let recurrence = goal.recurrence ?? Recurrence(frequency: .weekly, interval: 1)
    let interval = min(52, max(1, recurrence.interval))
    let base = parseDateOnly(goal.deadline) ?? now
    var deadline = base
    var iterations = 0
    repeat {
        switch recurrence.frequency {
        case .daily: deadline = Calendar.current.date(byAdding: .day, value: interval, to: deadline) ?? deadline
        case .weekly: deadline = Calendar.current.date(byAdding: .day, value: interval * 7, to: deadline) ?? deadline
        case .monthly: deadline = Calendar.current.date(byAdding: .month, value: interval, to: deadline) ?? deadline
        }
        iterations += 1
    } while deadline <= Calendar.current.startOfDay(for: now) && iterations < 120

    let dayDelta = calendarDays(from: base, to: deadline)
    let stamp = isoString(now)
    var next = goal
    next.id = UUID().uuidString
    next.seriesId = goal.seriesId ?? goal.id
    next.occurrence = (goal.occurrence ?? 1) + 1
    next.status = .active
    next.deadline = dateOnlyString(deadline)
    next.startDate = nil
    next.createdAt = stamp
    next.updatedAt = stamp
    next.completedAt = nil
    next.archivedAt = nil
    next.deletedAt = nil

    var milestoneMap: [String: String] = [:]
    next.milestones = goal.milestones.map { item in
        var copy = item
        let newID = UUID().uuidString
        milestoneMap[item.id] = newID
        copy.id = newID
        copy.completed = false
        copy.completedAt = nil
        if let due = parseDateOnly(item.dueDate) {
            copy.dueDate = dateOnlyString(Calendar.current.date(byAdding: .day, value: dayDelta, to: due) ?? due)
        }
        return copy
    }
    next.tasks = goal.tasks.map { item in
        var copy = item
        copy.id = UUID().uuidString
        copy.goalId = next.id
        copy.milestoneId = item.milestoneId.flatMap { milestoneMap[$0] }
        copy.parentTaskId = nil
        copy.completed = false
        copy.completedAt = nil
        copy.createdAt = stamp
        copy.updatedAt = stamp
        if let due = parseDateOnly(item.dueDate) {
            copy.dueDate = dateOnlyString(Calendar.current.date(byAdding: .day, value: dayDelta, to: due) ?? due)
        }
        return copy
    }
    // Re-map subtask parents to the freshly generated task IDs.
    var taskMap: [String: String] = [:]
    for (index, item) in goal.tasks.enumerated() {
        taskMap[item.id] = next.tasks[index].id
    }
    next.tasks = next.tasks.enumerated().map { index, item in
        var copy = item
        copy.parentTaskId = goal.tasks[index].parentTaskId.flatMap { taskMap[$0] }
        return copy
    }
    if next.finance != nil { next.finance?.currentAmount = 0 }
    return next
}
