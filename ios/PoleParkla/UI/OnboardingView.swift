import SwiftUI

struct OnboardingView: View {
    private enum ProfileField: Hashable { case name, phone, recipient }

    @Bindable var model: AppModel
    @State private var step = 0
    @State private var languageTag = ""
    @State private var name = ""
    @State private var phone = ""
    @State private var recipient = defaultReportRecipient
    @State private var showingPrivacy = false
    @State private var validationVisible = false
    @FocusState private var focusedField: ProfileField?

    var body: some View {
        VStack(spacing: 0) {
            progressIndicator.padding(.top, 14).padding(.bottom, 8)
            Group {
                if step == 0 { introStep }
                else { profileStep }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            footer
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(PpColor.canvas)
                .overlay(alignment: .top) { Divider().foregroundStyle(PpColor.outlineVariant) }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("common.done") { focusedField = nil }
            }
        }
        .sheet(isPresented: $showingPrivacy) {
            PrivacyView(languageTag: languageTag)
                .environment(\.locale, configuredLocale)
        }
        .onAppear {
            languageTag = model.settings.value.languageTag
            name = model.settings.value.profile.name
            phone = model.settings.value.profile.phone
            recipient = model.settings.value.defaultRecipient
        }
        .ppScreenBackground()
    }

    private var progressIndicator: some View {
        HStack(spacing: 8) {
            ForEach(0 ..< 2, id: \.self) { item in
                Capsule()
                    .fill(item == step ? PpColor.forest : PpColor.outline)
                    .frame(width: item == step ? 32 : 10, height: 8)
            }
        }
        .animation(.snappy, value: step)
        .accessibilityElement(children: .ignore)
        .accessibilityIdentifier("onboarding.progress")
        .accessibilityValue("\(step + 1) / 2")
    }

    private var introStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 14) {
                    Image("BrandMark")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 72, height: 72)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Pole parkla!")
                            .font(.title.bold())
                            .foregroundStyle(PpColor.ink)
                        Text("onboarding.intro.title")
                            .font(.headline)
                            .foregroundStyle(PpColor.forest)
                    }
                }
                Text("onboarding.intro.body")
                    .font(.body)
                    .foregroundStyle(PpColor.muted)

                PpCard {
                    Label {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("onboarding.safety.title").font(.headline)
                            Text("onboarding.safety.body")
                                .font(.subheadline)
                                .foregroundStyle(PpColor.muted)
                        }
                    } icon: {
                        Image(systemName: "figure.walk.motion")
                            .font(.title2)
                            .foregroundStyle(PpColor.forest)
                    }
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("onboarding.language.title").font(.title3.bold())
                    languageChoice("English", tag: "en")
                    languageChoice("Eesti", tag: "et")
                    languageChoice("Русский", tag: "ru")
                    Text("onboarding.language.body")
                        .font(.footnote)
                        .foregroundStyle(PpColor.muted)
                }

                Button {
                    showingPrivacy = true
                } label: {
                    Label("onboarding.privacy.open", systemImage: "hand.raised")
                }
                .buttonStyle(PpSecondaryButtonStyle())
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .scrollIndicators(.hidden)
    }

    private var profileStep: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("onboarding.profile.title")
                        .font(.largeTitle.bold())
                        .foregroundStyle(PpColor.ink)
                    Text("onboarding.profile.body")
                        .foregroundStyle(PpColor.muted)

                    PpField(title: "settings.name", text: $name, isError: validationVisible && isBlank(name))
                        .textContentType(.name)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.next)
                        .focused($focusedField, equals: .name)
                        .onSubmit { focusedField = .phone }
                        .id(ProfileField.name)

                    PpField(
                        title: "settings.phone",
                        text: $phone,
                        keyboard: .phonePad,
                        isError: validationVisible && isBlank(phone)
                    )
                    .textContentType(.telephoneNumber)
                    .focused($focusedField, equals: .phone)
                    .id(ProfileField.phone)

                    VStack(alignment: .leading, spacing: 7) {
                        PpField(
                            title: "settings.recipient",
                            text: $recipient,
                            keyboard: .emailAddress,
                            isError: validationVisible && isBlank(recipient)
                        )
                        .textContentType(.emailAddress)
                        .submitLabel(.done)
                        .focused($focusedField, equals: .recipient)
                        .onSubmit { focusedField = nil }
                        .id(ProfileField.recipient)
                        Text("onboarding.recipient.body")
                            .font(.footnote)
                            .foregroundStyle(PpColor.muted)
                    }

                    if validationVisible && !canComplete {
                        PpNotice(text: model.localized("onboarding.required"))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            .scrollIndicators(.hidden)
            .onChange(of: focusedField) { _, field in
                guard let field else { return }
                withAnimation { proxy.scrollTo(field, anchor: .center) }
            }
        }
    }

    private var footer: some View {
        HStack(spacing: 12) {
            if step == 1 {
                Button("common.back") {
                    focusedField = nil
                    withAnimation { step = 0 }
                }
                .buttonStyle(PpSecondaryButtonStyle())
            }
            Button(step == 0 ? "common.continue" : "common.done") {
                if step == 0 {
                    withAnimation { step = 1 }
                } else {
                    validationVisible = true
                    focusedField = nil
                    _ = model.completeOnboarding(
                        languageTag: languageTag,
                        profile: ReporterProfile(name: name, phone: phone),
                        recipient: recipient
                    )
                }
            }
            .buttonStyle(PpPrimaryButtonStyle())
            .accessibilityIdentifier(step == 0 ? "onboarding.continue" : "onboarding.done")
        }
    }

    private var canComplete: Bool {
        !isBlank(name) && !isBlank(phone) && !isBlank(recipient)
    }

    private var configuredLocale: Locale {
        languageTag.isEmpty ? .autoupdatingCurrent : Locale(identifier: languageTag)
    }

    private func languageChoice(_ title: String, tag: String) -> some View {
        Button {
            languageTag = tag
            model.saveLanguage(tag)
        } label: {
            HStack {
                Text(title).font(.headline)
                Spacer()
                Image(systemName: languageTag == tag ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(languageTag == tag ? PpColor.forest : PpColor.outline)
            }
            .frame(minHeight: 56)
            .padding(.horizontal, 16)
            .background(PpColor.surface, in: RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(languageTag == tag ? PpColor.forest : PpColor.outlineVariant, lineWidth: languageTag == tag ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(languageTag == tag ? [.isSelected] : [])
    }

    private func isBlank(_ value: String) -> Bool {
        value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
