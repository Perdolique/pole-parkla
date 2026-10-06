import SwiftUI

private enum SettingsDestination: String, Identifiable {
    case language, profile, recipient, templates, privacy
    var id: String { rawValue }
}

struct SettingsScreen: View {
    @Bindable var model: AppModel
    @State private var destination: SettingsDestination?

    var body: some View {
        List {
            category("settings.language", value: languageName, icon: "globe", destination: .language)
            category("settings.profile", value: profileSummary, icon: "person.crop.circle", destination: .profile)
            category("settings.recipient", value: model.settings.value.defaultRecipient, icon: "envelope", destination: .recipient)
            category("settings.templates", value: String(model.templates.count), icon: "doc.text", destination: .templates)
            category("settings.privacy", value: model.localized("privacy.local.title"), icon: "hand.raised", destination: .privacy)

            Link("settings.source", destination: URL(string: "https://github.com/Perdolique/pole-parkla")!)
                .buttonStyle(PpSecondaryButtonStyle())
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                .accessibilityIdentifier("settings.source")
            Link("settings.feedback", destination: URL(string: "https://github.com/Perdolique/pole-parkla/issues")!)
                .buttonStyle(PpSecondaryButtonStyle())
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                .accessibilityIdentifier("settings.feedback")

            HStack {
                Text("settings.version").foregroundStyle(PpColor.muted)
                Spacer()
                Text(appVersion).foregroundStyle(PpColor.muted)
            }
            .font(.footnote)
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle("settings.title")
        .ppScreenBackground()
        .sheet(item: $destination) { destination in
            switch destination {
            case .language: LanguageSettingsSheet(model: model)
            case .profile: ProfileSettingsSheet(model: model)
            case .recipient: RecipientSettingsSheet(model: model)
            case .templates: TemplatesSettingsSheet(model: model)
            case .privacy: PrivacyView(languageTag: model.settings.value.languageTag, model: model)
            }
        }
    }

    private var languageName: String {
        switch model.settings.value.languageTag {
        case "en": "English"
        case "et": "Eesti"
        case "ru": "Русский"
        default: model.localized("language.system")
        }
    }

    private var profileSummary: String {
        let profile = model.settings.value.profile
        return [profile.name, profile.phone].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private var appVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
        return "\(version) (\(build))"
    }

    private func category(
        _ title: LocalizedStringKey,
        value: String,
        icon: String,
        destination: SettingsDestination
    ) -> some View {
        PpDisclosureRow(title: title, value: value, systemImage: icon) {
            self.destination = destination
        }
        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
        .accessibilityIdentifier("settings.\(destination.rawValue)")
    }
}

private struct LanguageSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var languageTag: String

    init(model: AppModel) {
        self.model = model
        _languageTag = State(initialValue: model.settings.value.languageTag)
    }

    var body: some View {
        SettingsSheet(title: "settings.language", save: {
            model.saveLanguage(languageTag)
            dismiss()
        }) {
            VStack(spacing: 10) {
                choice("language.system", tag: "")
                choice("English", tag: "en")
                choice("Eesti", tag: "et")
                choice("Русский", tag: "ru")
            }
        }
    }

    private func choice(_ title: LocalizedStringKey, tag: String) -> some View {
        PpChoiceRow(title: title, systemImage: "globe", selected: languageTag == tag) { languageTag = tag }
    }
}

private struct ProfileSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var profile: ReporterProfile

    init(model: AppModel) {
        self.model = model
        _profile = State(initialValue: model.settings.value.profile)
    }

    var body: some View {
        SettingsSheet(title: "settings.profile", save: {
            model.saveProfile(profile)
            dismiss()
        }) {
            VStack(spacing: 12) {
                PpField(title: "settings.name", text: $profile.name)
                    .textContentType(.name)
                PpField(title: "settings.phone", text: $profile.phone, keyboard: .phonePad)
                    .textContentType(.telephoneNumber)
            }
        }
    }
}

private struct RecipientSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var recipient: String

    init(model: AppModel) {
        self.model = model
        _recipient = State(initialValue: model.settings.value.defaultRecipient)
    }

    var body: some View {
        SettingsSheet(title: "settings.recipient", save: {
            model.saveDefaultRecipient(recipient)
            dismiss()
        }, canSave: !recipient.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            PpField(title: "settings.recipient", text: $recipient, keyboard: .emailAddress)
                .textContentType(.emailAddress)
        }
    }
}

private struct TemplatesSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var editor: TemplateDraft?
    @State private var pendingDeletion: CustomViolationTemplate?

    var body: some View {
        NavigationStack {
            List {
                if model.templates.isEmpty {
                    ContentUnavailableView("settings.templates.empty", systemImage: "doc.text")
                        .listRowBackground(Color.clear)
                } else {
                    ForEach(model.templates) { template in
                        HStack {
                            Button {
                                editor = TemplateDraft(template)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(template.displayName).font(.headline)
                                    Text(template.estonianDescription).font(.subheadline).foregroundStyle(PpColor.muted).lineLimit(2)
                                }
                                .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                            Menu {
                                Button("violation.custom.edit") { editor = TemplateDraft(template) }
                                Button("common.delete", role: .destructive) { pendingDeletion = template }
                            } label: {
                                Image(systemName: "ellipsis.circle").frame(width: 48, height: 48)
                            }
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .navigationTitle("settings.templates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("common.done") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    Button { editor = TemplateDraft() } label: { Image(systemName: "plus") }
                        .accessibilityLabel("violation.custom.add")
                }
            }
            .sheet(item: $editor) { draft in TemplateEditorSheet(model: model, draft: draft) }
            .confirmationDialog(
                "violation.custom.delete.confirm.title",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible
            ) {
                Button("common.delete", role: .destructive) {
                    if let pendingDeletion { model.deleteTemplate(pendingDeletion) }
                    pendingDeletion = nil
                }
                Button("common.cancel", role: .cancel) { pendingDeletion = nil }
            } message: { Text("violation.custom.delete.confirm.body") }
            .ppScreenBackground()
        }
    }
}

private struct TemplateDraft: Identifiable {
    var id = UUID()
    var templateID: String?
    var name = ""
    var description = ""

    init() {}
    init(_ template: CustomViolationTemplate) {
        templateID = template.id
        name = template.displayName
        description = template.estonianDescription
    }
}

private struct TemplateEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var templateID: String?
    @State private var name: String
    @State private var description: String

    init(model: AppModel, draft: TemplateDraft) {
        self.model = model
        _templateID = State(initialValue: draft.templateID)
        _name = State(initialValue: draft.name)
        _description = State(initialValue: draft.description)
    }

    var body: some View {
        SettingsSheet(title: templateID == nil ? "violation.custom.add" : "violation.custom.edit", save: {
            model.saveTemplate(id: templateID, name: name, description: description)
            dismiss()
        }, canSave: !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            VStack(spacing: 12) {
                PpField(title: "violation.custom.name", text: $name)
                PpField(title: "violation.custom.description.et", text: $description, axis: .vertical)
                    .frame(minHeight: 120)
            }
        }
    }
}

private struct SettingsSheet<Content: View>: View {
    @Environment(\.dismiss) private var dismiss
    let title: LocalizedStringKey
    let save: () -> Void
    var canSave = true
    @ViewBuilder let content: Content

    var body: some View {
        NavigationStack {
            ScrollView {
                content.padding(20)
            }
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom) {
                Button("common.save", action: save)
                    .buttonStyle(PpPrimaryButtonStyle())
                    .disabled(!canSave)
                    .padding(20)
                    .background(PpColor.canvas)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("common.cancel") { dismiss() } } }
            .ppScreenBackground()
        }
    }
}

struct PrivacyView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale
    let languageTag: String
    var model: AppModel?
    @State private var showingDeleteConfirmation = false

    init(languageTag: String, model: AppModel? = nil) {
        self.languageTag = languageTag
        self.model = model
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    privacySection("privacy.local.title", "privacy.local.body")
                    privacySection("privacy.location.title", "privacy.location.body")
                    privacySection("privacy.map.title", "privacy.map.body")
                    privacySection("privacy.mail.title", "privacy.mail.body")
                    Link("privacy.full.open", destination: privacyURL).frame(minHeight: 48)
                    if model != nil {
                        Button("settings.delete.all", role: .destructive) { showingDeleteConfirmation = true }
                            .buttonStyle(PpDangerButtonStyle())
                    }
                }
                .padding(20)
            }
            .navigationTitle("privacy.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("common.done") { dismiss() } }
            .confirmationDialog("settings.delete.confirm.title", isPresented: $showingDeleteConfirmation, titleVisibility: .visible) {
                Button("settings.delete.all", role: .destructive) {
                    guard let model else { return }
                    Task {
                        if await model.deleteAllData() { dismiss() }
                    }
                }
                Button("common.cancel", role: .cancel) {}
            } message: { Text("settings.delete.confirm.body") }
            .ppScreenBackground()
        }
    }

    private var privacyURL: URL {
        let selectedLanguage = languageTag.isEmpty ? locale.language.languageCode?.identifier : languageTag
        let path = switch selectedLanguage {
        case "et": "privacy/"
        case "ru": "ru/privacy/"
        default: "en/privacy/"
        }
        return URL(string: "https://poleparkla.ee/\(path)")!
    }

    private func privacySection(_ title: LocalizedStringKey, _ body: LocalizedStringKey) -> some View {
        PpCard {
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.headline)
                Text(body).foregroundStyle(PpColor.muted)
            }
        }
    }
}
