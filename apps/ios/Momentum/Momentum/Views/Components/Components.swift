import SwiftUI

struct MomentumCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(AppTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(AppTheme.line, lineWidth: 1)
            }
    }
}

extension View {
    func momentumCard() -> some View { modifier(MomentumCard()) }
}

struct ProgressRing: View {
    let percent: Int
    var accent: Color = Color(hex: "#3b82f6")
    var size: CGFloat = 76
    var lineWidth: CGFloat = 8

    private var tone: ProgressTone { progressTone(percent) }

    var body: some View {
        ZStack {
            Circle()
                .stroke(accent.opacity(0.16), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: CGFloat(min(max(percent, 0), 100)) / 100)
                .stroke(AppTheme.toneColor(tone, accent: accent), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text("\(percent)%")
                    .font(.system(size: size * 0.22, weight: .bold, design: .rounded))
                    .foregroundStyle(AppTheme.primaryText)
                Text("done")
                    .font(.system(size: size * 0.11, weight: .medium))
                    .foregroundStyle(AppTheme.tertiaryText)
            }
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Progress \(percent) percent")
    }
}

struct MetricTile: View {
    let title: String
    let value: String
    var systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: systemImage)
                .font(.caption)
                .foregroundStyle(AppTheme.tertiaryText)
                .lineLimit(1)
            Text(value)
                .font(.title3.weight(.bold))
                .foregroundStyle(AppTheme.primaryText)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .momentumCard()
    }
}

struct SectionHeader: View {
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack {
            Text(title)
                .font(.headline)
                .foregroundStyle(AppTheme.primaryText)
            Spacer()
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(.subheadline.weight(.semibold))
                }
            }
        }
    }
}

struct EmptyState: View {
    let systemImage: String
    let title: String
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 36))
                .foregroundStyle(AppTheme.tertiaryText)
            Text(title)
                .font(.headline)
                .foregroundStyle(AppTheme.primaryText)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(AppTheme.secondaryText)
                .multilineTextAlignment(.center)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                }
                .buttonStyle(.borderedProminent)
                .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(30)
        .momentumCard()
    }
}

struct GoalCard: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState

    let goal: Goal
    private var percent: Int { goalPercent(goal) }
    private var category: Category? { store.category(id: goal.categoryId) }

    var body: some View {
        Button { nav.openGoal(goal.id) } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    if let category {
                        Image(systemName: category.icon.symbolName)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Color(hex: category.color))
                            .frame(width: 30, height: 30)
                            .background(Color(hex: category.color).opacity(0.14), in: RoundedRectangle(cornerRadius: 9))
                    }
                    Spacer()
                    Text(goal.priority.label.uppercased())
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(AppTheme.tertiaryText)
                }
                Text(goal.title)
                    .font(.headline)
                    .foregroundStyle(AppTheme.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                if !goal.description.isEmpty {
                    Text(goal.description)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.secondaryText)
                        .lineLimit(2)
                }
                HStack(spacing: 8) {
                    Text("\(goal.tasks.filter(\.completed).count)/\(goal.tasks.count) tasks")
                        .font(.caption)
                        .foregroundStyle(AppTheme.tertiaryText)
                    if let due = deadlineLabel(goal.deadline) {
                        Text("•")
                        Text(due)
                            .font(.caption)
                            .foregroundStyle(due.contains("overdue") ? AppTheme.danger : AppTheme.tertiaryText)
                    }
                    Spacer()
                    Text("\(percent)%")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(AppTheme.toneColor(progressTone(percent), accent: Color(hex: store.settings.accent)))
                }
                ProgressView(value: Double(percent), total: 100)
                    .tint(AppTheme.toneColor(progressTone(percent), accent: Color(hex: store.settings.accent)))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .momentumCard()
        }
        .buttonStyle(.plain)
    }
}

struct TaskRow: View {
    @EnvironmentObject private var store: MomentumStore

    let goalID: String
    let task: GoalTask
    var showsGoal: Bool = false

    private var isOverdue: Bool {
        guard let due = parseDateOnly(task.dueDate) else { return false }
        return !task.completed && due < Calendar.current.startOfDay(for: Date())
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Button {
                store.toggleTask(goalID: goalID, taskID: task.id)
            } label: {
                Image(systemName: task.completed ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(task.completed ? AppTheme.success : AppTheme.tertiaryText)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(task.completed ? "Reopen task" : "Complete task")

            VStack(alignment: .leading, spacing: 4) {
                Text(task.title)
                    .font(.body)
                    .foregroundStyle(task.completed ? AppTheme.tertiaryText : AppTheme.primaryText)
                    .strikethrough(task.completed)
                HStack(spacing: 8) {
                    if showsGoal, let goal = store.goal(id: task.goalId) {
                        Text(goal.title).lineLimit(1)
                    }
                    if let due = deadlineLabel(task.dueDate) {
                        Text(due)
                            .foregroundStyle(isOverdue ? AppTheme.danger : AppTheme.tertiaryText)
                    }
                    Text(task.priority.label)
                        .foregroundStyle(AppTheme.tertiaryText)
                }
                .font(.caption)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }
}

struct TextPromptSheet: View {
    @Environment(\.dismiss) private var dismiss

    let title: String
    let placeholder: String
    let confirmTitle: String
    var showsPriority = false
    var showsDueDate = false
    let onSave: (String, Priority, String?) -> Void

    @State private var text = ""
    @State private var priority: Priority = .medium
    @State private var hasDueDate = false
    @State private var dueDate = Date()

    var body: some View {
        NavigationStack {
            Form {
                Section(title) {
                    TextField(placeholder, text: $text)
                    if showsPriority {
                        Picker("Priority", selection: $priority) {
                            ForEach(Priority.allCases) { Text($0.label).tag($0) }
                        }
                    }
                    if showsDueDate {
                        Toggle("Due date", isOn: $hasDueDate)
                        if hasDueDate {
                            DatePicker("Due", selection: $dueDate, displayedComponents: .date)
                        }
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(confirmTitle) {
                        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !cleaned.isEmpty else { return }
                        onSave(cleaned, priority, hasDueDate ? dateOnlyString(dueDate) : nil)
                        dismiss()
                    }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
