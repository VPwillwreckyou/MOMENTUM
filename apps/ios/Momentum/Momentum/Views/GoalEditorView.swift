import SwiftUI

struct GoalEditorTarget: Identifiable {
    let id = UUID()
    let goal: Goal?

    static var new: GoalEditorTarget { GoalEditorTarget(goal: nil) }
    static func edit(_ goal: Goal) -> GoalEditorTarget { GoalEditorTarget(goal: goal) }
}

struct GoalEditorView: View {
    @EnvironmentObject private var store: MomentumStore
    @Environment(\.dismiss) private var dismiss

    let target: GoalEditorTarget

    @State private var title = ""
    @State private var description = ""
    @State private var notes = ""
    @State private var categoryID: String?
    @State private var kind: GoalKind = .oneTime
    @State private var frequency: RecurrenceFrequency = .weekly
    @State private var interval = 1
    @State private var priority: Priority = .medium
    @State private var hasStart = false
    @State private var startDate = Date()
    @State private var hasDeadline = false
    @State private var deadline = Calendar.current.date(byAdding: .month, value: 1, to: Date()) ?? Date()
    @State private var errorMessage: String?

    private var isValid: Bool {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= 120 && description.count <= 4000 && notes.count <= 10_000
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Goal") {
                    TextField("What do you want to achieve?", text: $title)
                    TextField("Short description", text: $description, axis: .vertical)
                        .lineLimit(2...4)
                    Picker("Category", selection: $categoryID) {
                        Text("None").tag(nil as String?)
                        ForEach(store.liveCategories) { Text($0.name).tag($0.id as String?) }
                    }
                    Picker("Priority", selection: $priority) {
                        ForEach(Priority.allCases) { Text($0.label).tag($0) }
                    }
                }

                Section("Plan") {
                    Picker("Type", selection: $kind) {
                        ForEach([GoalKind.oneTime, .recurring], id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    if kind == .recurring {
                        Picker("Repeats", selection: $frequency) {
                            ForEach(RecurrenceFrequency.allCases) { Text($0.label).tag($0) }
                        }
                        Stepper("Every \(interval) \(frequency.rawValue)\(interval == 1 ? "" : "s")", value: $interval, in: 1...52)
                    }
                    Toggle("Start date", isOn: $hasStart)
                    if hasStart { DatePicker("Starts", selection: $startDate, displayedComponents: .date) }
                    Toggle("Deadline", isOn: $hasDeadline)
                    if hasDeadline { DatePicker("Due", selection: $deadline, displayedComponents: .date) }
                }

                Section("Notes") {
                    TextEditor(text: $notes)
                        .frame(minHeight: 90)
                    Text("\(notes.count)/10,000").font(.caption).foregroundStyle(AppTheme.tertiaryText)
                }

                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(AppTheme.danger).font(.caption) }
                }
            }
            .navigationTitle(target.goal == nil ? "New goal" : "Edit goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(!isValid)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let goal = target.goal else { return }
        title = goal.title
        description = goal.description
        notes = goal.notes
        categoryID = goal.categoryId
        kind = goal.kind
        priority = goal.priority
        frequency = goal.recurrence?.frequency ?? .weekly
        interval = goal.recurrence?.interval ?? 1
        if let start = parseDateOnly(goal.startDate) {
            hasStart = true
            startDate = start
        }
        if let due = parseDateOnly(goal.deadline) {
            hasDeadline = true
            deadline = due
        }
    }

    private func save() {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty, cleanTitle.count <= 120 else {
            errorMessage = "Give the goal a title of 120 characters or fewer."
            return
        }
        let startValue = hasStart ? dateOnlyString(startDate) : nil
        let deadlineValue = hasDeadline ? dateOnlyString(deadline) : nil
        if let startValue, let deadlineValue, startValue > deadlineValue {
            errorMessage = "The start date must be before the deadline."
            return
        }

        if let existing = target.goal {
            var updated = existing
            updated.title = cleanTitle
            updated.description = String(description.prefix(4000))
            updated.notes = String(notes.prefix(10_000))
            updated.categoryId = categoryID
            updated.kind = kind
            updated.recurrence = kind == .recurring
                ? Recurrence(frequency: frequency, interval: min(52, max(1, interval)))
                : nil
            updated.priority = priority
            updated.startDate = startValue
            updated.deadline = deadlineValue
            store.updateGoal(updated)
        } else {
            _ = store.addGoal(GoalDraft(
                title: cleanTitle, description: String(description.prefix(4000)),
                notes: String(notes.prefix(10_000)), categoryID: categoryID,
                kind: kind,
                recurrence: kind == .recurring
                    ? Recurrence(frequency: frequency, interval: min(52, max(1, interval)))
                    : nil,
                priority: priority, startDate: startValue, deadline: deadlineValue
            ))
        }
        dismiss()
    }
}
