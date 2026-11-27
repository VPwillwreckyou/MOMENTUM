import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var store: MomentumStore
    @EnvironmentObject private var nav: NavigationState

    @State private var exportDocument: BackupDocument?
    @State private var showingExporter = false
    @State private var showingImporter = false
    @State private var pendingImport: Data?
    @State private var showingErase = false
    @State private var eraseText = ""
    @State private var showingRestoreConfirm = false

    private var accent: Color { Color(hex: store.settings.accent) }

    var body: some View {
        Form {
            Section("Appearance") {
                Picker("Theme", selection: Binding(
                    get: { store.settings.themeMode },
                    set: { mode in store.updateSettings { $0.themeMode = mode } }
                )) {
                    ForEach(ThemeMode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Accent").font(.subheadline).foregroundStyle(AppTheme.secondaryText)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 8)], spacing: 8) {
                        ForEach(AccentPreset.all) { preset in
                            Button {
                                store.updateSettings {
                                    $0.accent = preset.hex
                                    $0.accentSecondary = preset.secondary
                                }
                            } label: {
                                Circle()
                                    .fill(Color(hex: preset.hex))
                                    .frame(width: 26, height: 26)
                                    .overlay {
                                        if store.settings.accent == preset.hex {
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.bold))
                                                .foregroundStyle(AppTheme.contrastText(for: preset.hex))
                                        }
                                    }
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .buttonStyle(.plain)
                            .accessibilityLabel(preset.name)
                        }
                    }
                    .padding(.vertical, 4)
                }

                Picker("Density", selection: Binding(
                    get: { store.settings.density },
                    set: { value in store.updateSettings { $0.density = value } }
                )) {
                    ForEach(Density.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                Picker("Week starts", selection: Binding(
                    get: { store.settings.weekStartsOn },
                    set: { value in store.updateSettings { $0.weekStartsOn = value } }
                )) {
                    Text("Monday").tag(1)
                    Text("Sunday").tag(0)
                }
                .pickerStyle(.segmented)

                Toggle("Reduce motion", isOn: Binding(
                    get: { store.settings.reduceMotion },
                    set: { value in store.updateSettings { $0.reduceMotion = value } }
                ))

                TextField("Display name", text: Binding(
                    get: { store.settings.displayName },
                    set: { value in store.updateSettings { $0.displayName = String(value.prefix(60)) } }
                ))
            }

            Section("Library") {
                Button { nav.showArchive = true } label: {
                    Label("Archive", systemImage: "archivebox")
                }
                Button { nav.showTrash = true } label: {
                    Label("Recently deleted", systemImage: "trash")
                }
            }

            Section("Data & privacy") {
                Button {
                    exportDocument = store.exportBackupData().map { BackupDocument(data: $0) }
                    showingExporter = exportDocument != nil
                } label: {
                    Label("Export JSON backup", systemImage: "square.and.arrow.up")
                }
                Button { showingImporter = true } label: {
                    Label("Import JSON backup", systemImage: "square.and.arrow.down")
                }
                Button { showingRestoreConfirm = true } label: {
                    Label("Restore sample workspace", systemImage: "sparkles")
                }
                Button(role: .destructive) { eraseText = ""; showingErase = true } label: {
                    Label("Erase all data", systemImage: "trash.slash")
                }
            }

            Section("About") {
                HStack { Text("Version"); Spacer(); Text("1.0.0").foregroundStyle(AppTheme.secondaryText) }
                Text("This native iOS app is fully offline. Goals, tasks and categories live only on this device. AI planning, accounts and cloud sync stay in the web app and Capacitor shell.")
                    .font(.caption).foregroundStyle(AppTheme.secondaryText)
            }
        }
        .navigationTitle("Settings")
        .fileExporter(
            isPresented: $showingExporter,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "momentum-backup.json"
        ) { result in
            if case let .failure(error) = result { store.storageError = error.localizedDescription }
        }
        .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.json]) { result in
            switch result {
            case let .success(url):
                guard url.startAccessingSecurityScopedResource() else { return }
                defer { url.stopAccessingSecurityScopedResource() }
                if let data = try? Data(contentsOf: url) { pendingImport = data }
            case let .failure(error):
                store.storageError = error.localizedDescription
            }
        }
        .alert("Replace workspace?", isPresented: Binding(
            get: { pendingImport != nil },
            set: { if !$0 { pendingImport = nil } }
        )) {
            Button("Cancel", role: .cancel) { pendingImport = nil }
            Button("Import", role: .destructive) {
                if let data = pendingImport { _ = store.importBackupData(data) }
                pendingImport = nil
            }
        } message: {
            Text("This replaces every goal, category and setting currently on this device.")
        }
        .alert("Restore sample workspace?", isPresented: $showingRestoreConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Restore", role: .destructive) { store.restoreSampleWorkspace() }
        }
        .alert("Erase everything?", isPresented: $showingErase) {
            TextField("Type ERASE", text: $eraseText)
            Button("Cancel", role: .cancel) {}
            Button("Erase", role: .destructive) {
                if eraseText == "ERASE" {
                    store.eraseAll()
                    nav.resetPath()
                }
            }
        } message: {
            Text("All goals, tasks and categories will be deleted. Type ERASE to confirm.")
        }
    }
}
