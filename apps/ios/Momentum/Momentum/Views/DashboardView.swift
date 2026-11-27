import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState

    private var accent: Color { Color(hex: store.settings.accent) }
    private var score: Int { momentumScore(store.workspace.goals) }
    private var nextTask: GoalTask? { focusTasks(store.workspace.goals, limit: 1).first }
    private var series: [(label: String, count: Int)] { completionSeries(store.workspace.goals, days: 7) }
    private var maxCount: Int { max(1, series.map(\.count).max() ?? 1) }

    private var deadlines: [Goal] {
        store.activeGoals
            .filter { $0.deadline != nil }
            .sorted { ($0.deadline ?? "") < ($1.deadline ?? "") }
            .prefix(4)
            .map { $0 }
    }

    private var recentActivity: [Activity] {
        store.workspace.activities.sorted { $0.at > $1.at }.prefix(5).map { $0 }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                momentumCard
                nextMoveCard
                if !store.activeGoals.isEmpty {
                    activeGoalsSection
                }
                completionCard
                if !deadlines.isEmpty { deadlinesSection }
                if !recentActivity.isEmpty { activitySection }
            }
            .padding(16)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("Overview")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { nav.showSearch = true } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel("Search")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { nav.newGoal() } label: {
                    Image(systemName: "plus.circle.fill").font(.title3)
                }
                .accessibilityLabel("New goal")
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(greeting())\(store.settings.displayName.isEmpty ? "" : ", \(store.settings.displayName)")")
                .font(.title2.weight(.bold))
                .foregroundStyle(AppTheme.primaryText)
            Text(todayLabel())
                .font(.subheadline)
                .foregroundStyle(AppTheme.secondaryText)
        }
    }

    private var momentumCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 16) {
                ProgressRing(percent: score, accent: accent, size: 92, lineWidth: 9)
                VStack(alignment: .leading, spacing: 5) {
                    Text("Momentum signal")
                        .font(.headline)
                        .foregroundStyle(AppTheme.primaryText)
                    Text(momentumCaption(score))
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.secondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 10) {
                MetricTile(title: "Active goals", value: "\(store.activeGoals.count)", systemImage: "target")
                MetricTile(title: "Done today", value: "\(completedTodayCount(store.workspace.goals))", systemImage: "checkmark.circle.fill")
                MetricTile(title: "Last 7 days", value: "\(series.reduce(0) { $0 + $1.count })", systemImage: "flame.fill")
            }
        }
        .padding(16)
        .momentumCard()
    }

    private var nextMoveCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Next best move", systemImage: "arrow.right.circle.fill")
                .font(.headline)
                .foregroundStyle(accent)
            if let task = nextTask, let goal = store.goal(id: task.goalId) {
                HStack(alignment: .top, spacing: 12) {
                    Button {
                        store.toggleTask(goalID: goal.id, taskID: task.id)
                    } label: {
                        Image(systemName: "circle")
                            .font(.title3)
                            .foregroundStyle(AppTheme.tertiaryText)
                    }
                    .buttonStyle(.plain)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(task.title)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(AppTheme.primaryText)
                        Button { nav.openGoal(goal.id) } label: {
                            Text(goal.title)
                                .font(.caption)
                                .foregroundStyle(accent)
                                .lineLimit(1)
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer()
                }
                .padding(14)
                .background(AppTheme.surfaceElevated, in: RoundedRectangle(cornerRadius: 14))
            } else {
                Text("Everything is clear. Add a goal when you are ready.")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.secondaryText)
            }
        }
        .padding(16)
        .momentumCard()
    }

    private var activeGoalsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Active goals", actionTitle: "See all") { nav.tab = .goals }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 250), spacing: 14)], spacing: 14) {
                ForEach(store.activeGoals) { goal in
                    GoalCard(goal: goal)
                }
            }
        }
    }

    private var completionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Task completions")
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(Array(series.enumerated()), id: \.offset) { _, day in
                    VStack(spacing: 6) {
                        VStack {
                            Spacer(minLength: 0)
                            RoundedRectangle(cornerRadius: 5)
                                .fill(accent.opacity(day.count == 0 ? 0.18 : 0.85))
                                .frame(height: max(6, CGFloat(day.count) / CGFloat(maxCount) * 74))
                        }
                        .frame(height: 74)
                        Text(day.label)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.tertiaryText)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(16)
        .momentumCard()
    }

    private var deadlinesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: "Deadlines")
            ForEach(deadlines) { goal in
                Button { nav.openGoal(goal.id) } label: {
                    HStack {
                        Image(systemName: "clock")
                            .foregroundStyle(AppTheme.tertiaryText)
                        Text(goal.title)
                            .foregroundStyle(AppTheme.primaryText)
                            .lineLimit(1)
                        Spacer()
                        Text(deadlineLabel(goal.deadline) ?? "")
                            .font(.caption)
                            .foregroundStyle(AppTheme.secondaryText)
                    }
                    .padding(12)
                    .background(AppTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var activitySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: "Recent activity")
            ForEach(recentActivity) { item in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(accent.opacity(0.6))
                        .frame(width: 8, height: 8)
                        .padding(.top, 6)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.message)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.primaryText)
                            .lineLimit(2)
                        Text("\(store.goal(id: item.goalId)?.title ?? "Goal") · \(activityTimeLabel(item.at))")
                            .font(.caption)
                            .foregroundStyle(AppTheme.tertiaryText)
                    }
                    Spacer()
                }
            }
        }
        .padding(16)
        .momentumCard()
    }
}
