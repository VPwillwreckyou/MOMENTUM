import XCTest
@testable import Momentum

final class MomentumTests: XCTestCase {
    private func makeGoal(tasks: [Bool], milestones: [Bool], status: GoalStatus = .active) -> Goal {
        let stamp = isoString()
        return Goal(
            id: "g1", title: "Test goal", description: "", notes: "", categoryId: nil,
            status: status, kind: .oneTime, recurrence: nil, priority: .medium,
            startDate: nil, deadline: nil, createdAt: stamp, updatedAt: stamp,
            completedAt: nil, archivedAt: nil, deletedAt: nil, seriesId: nil, occurrence: 1,
            milestones: milestones.enumerated().map { index, done in
                Milestone(id: "m\(index)", title: "M\(index)", description: nil, dueDate: nil,
                          completed: done, completedAt: done ? stamp : nil, order: index)
            },
            tasks: tasks.enumerated().map { index, done in
                GoalTask(id: "t\(index)", goalId: "g1", milestoneId: nil, parentTaskId: nil,
                         title: "T\(index)", notes: nil, completed: done,
                         completedAt: done ? stamp : nil, dueDate: nil, priority: .medium,
                         order: index, createdAt: stamp, updatedAt: stamp)
            },
            finance: nil
        )
    }

    func testProgressUsesTaskAndMilestoneWeights() {
        XCTAssertEqual(goalPercent(makeGoal(tasks: [], milestones: [])), 0)
        XCTAssertEqual(goalPercent(makeGoal(tasks: [true, false], milestones: [])), 50)
        XCTAssertEqual(goalPercent(makeGoal(tasks: [], milestones: [true, false, false, false])), 25)
        XCTAssertEqual(goalPercent(makeGoal(tasks: [true, true], milestones: [false, false])), 65)
        XCTAssertEqual(goalPercent(makeGoal(tasks: [false], milestones: [false], status: .completed)), 100)
    }

    func testProgressTones() {
        XCTAssertEqual(progressTone(100), .success)
        XCTAssertEqual(progressTone(60), .accent)
        XCTAssertEqual(progressTone(25), .warning)
        XCTAssertEqual(progressTone(5), .danger)
    }

    func testMomentumScoreIsZeroWithoutActiveGoals() {
        XCTAssertEqual(momentumScore([makeGoal(tasks: [true], milestones: [], status: .completed)]), 0)
    }

    func testMomentumScoreRisesWithRecentCompletion() {
        let goal = makeGoal(tasks: [true, false, false, false], milestones: [])
        XCTAssertGreaterThan(momentumScore([goal]), 0)
        XCTAssertLessThanOrEqual(momentumScore([goal]), 100)
    }

    func testDeadlineLabels() {
        let now = Date()
        let today = dateOnlyString(now)
        let tomorrow = dateOnlyString(Calendar.current.date(byAdding: .day, value: 1, to: now)!)
        let yesterday = dateOnlyString(Calendar.current.date(byAdding: .day, value: -1, to: now)!)
        XCTAssertEqual(deadlineLabel(today, now: now), "Due today")
        XCTAssertEqual(deadlineLabel(tomorrow, now: now), "Due tomorrow")
        XCTAssertEqual(deadlineLabel(yesterday, now: now), "1 day overdue")
        XCTAssertNil(deadlineLabel(nil, now: now))
    }

    func testRecurringCompletionCreatesNextOccurrence() {
        var goal = makeGoal(tasks: [true, false], milestones: [true])
        goal.kind = .recurring
        goal.recurrence = Recurrence(frequency: .weekly, interval: 1)
        goal.deadline = dateOnlyString(Calendar.current.date(byAdding: .day, value: -7, to: Date())!)
        let next = nextOccurrence(from: goal)
        XCTAssertNotNil(next)
        XCTAssertEqual(next?.seriesId, goal.id)
        XCTAssertEqual(next?.occurrence, 2)
        XCTAssertEqual(next?.tasks.count, goal.tasks.count)
        XCTAssertTrue(next?.tasks.allSatisfy { !$0.completed } ?? false)
        XCTAssertGreaterThan(parseDateOnly(next?.deadline) ?? .distantPast, Date())
    }

    func testStorePersistsAcrossInstances() {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("momentum-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let store = MomentumStore(directory: directory, seedIfMissing: false)
        XCTAssertTrue(store.workspace.goals.isEmpty)
        let goal = store.addGoal(GoalDraft(
            title: "Persisted goal", description: "", notes: "", categoryID: nil,
            kind: .oneTime, recurrence: nil, priority: .high, startDate: nil, deadline: nil
        ))
        store.addTask(goalID: goal.id, title: "Persisted task")

        let reloaded = MomentumStore(directory: directory, seedIfMissing: false)
        XCTAssertEqual(reloaded.workspace.goals.count, 1)
        XCTAssertEqual(reloaded.workspace.goals.first?.tasks.count, 1)
        XCTAssertEqual(reloaded.workspace.goals.first?.title, "Persisted goal")
        XCTAssertNil(reloaded.storageError)
    }

    func testBackupRoundTrip() {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("momentum-backup-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let store = MomentumStore(directory: directory, seedIfMissing: false)
        _ = store.addGoal(GoalDraft(
            title: "Backup goal", description: "Keep me", notes: "", categoryID: nil,
            kind: .oneTime, recurrence: nil, priority: .medium, startDate: nil, deadline: nil
        ))
        guard let data = store.exportBackupData() else {
            XCTFail("Expected backup data")
            return
        }
        let other = MomentumStore(
            directory: directory.appendingPathComponent("other", isDirectory: true),
            seedIfMissing: false
        )
        XCTAssertTrue(other.importBackupData(data))
        XCTAssertEqual(other.workspace.goals.first?.title, "Backup goal")
    }
}
