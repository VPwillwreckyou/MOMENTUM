import XCTest

final class MomentumUITests: XCTestCase {
    func testOnboardingGoalCreationAndTaskCompletionRespondToTaps() {
        let app = XCUIApplication()
        app.launchArguments.append("--ui-testing")
        app.launch()

        let continueButton = app.buttons["Continue"]
        XCTAssertTrue(continueButton.waitForExistence(timeout: 10))
        continueButton.tap()
        XCTAssertTrue(app.staticTexts["Make it yours"].waitForExistence(timeout: 5))
        app.buttons["Continue"].tap()
        XCTAssertTrue(app.staticTexts["What is the first move?"].waitForExistence(timeout: 5))
        app.buttons["Skip"].tap()

        let goalsTab = app.tabBars.buttons["Goals"]
        XCTAssertTrue(goalsTab.waitForExistence(timeout: 10))
        goalsTab.tap()
        app.buttons["New goal"].tap()

        let goalTitle = app.textFields["What do you want to achieve?"]
        XCTAssertTrue(goalTitle.waitForExistence(timeout: 5))
        goalTitle.tap()
        goalTitle.typeText("Touch test goal")
        app.buttons["Save"].tap()

        let goalCard = app.buttons.containing(.staticText, identifier: "Touch test goal").firstMatch
        XCTAssertTrue(goalCard.waitForExistence(timeout: 10))
        goalCard.tap()
        app.buttons["Add task"].tap()

        let taskTitle = app.textFields["What needs to happen?"]
        XCTAssertTrue(taskTitle.waitForExistence(timeout: 5))
        taskTitle.tap()
        taskTitle.typeText("Tap to complete")
        app.buttons["Add"].tap()

        let completeTask = app.buttons["Complete task"].firstMatch
        XCTAssertTrue(completeTask.waitForExistence(timeout: 10))
        completeTask.tap()
        let showCompleted = app.buttons["Show 1 completed"]
        XCTAssertTrue(showCompleted.waitForExistence(timeout: 5))
        showCompleted.tap()
        XCTAssertTrue(app.buttons["Reopen task"].waitForExistence(timeout: 5))

        app.navigationBars.buttons["Back"].tap()
        for tab in ["Overview", "Planner", "Categories", "Settings"] {
            let tabButton = app.tabBars.buttons[tab]
            XCTAssertTrue(tabButton.exists, "Missing \(tab) tab")
            tabButton.tap()
            XCTAssertTrue(tabButton.isSelected, "\(tab) tab did not become selected")
        }
    }
}
