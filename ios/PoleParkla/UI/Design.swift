import SwiftUI

enum PpColor {
    static let canvas = dynamic(light: 0xF3F5EF, dark: 0x0C110E)
    static let surface = dynamic(light: 0xFFFFFF, dark: 0x151C18)
    static let ink = dynamic(light: 0x111713, dark: 0xF3F6F1)
    static let forest = dynamic(light: 0x174B38, dark: 0x76D6AA)
    static let signal = dynamic(light: 0xD8FF63, dark: 0xCFF45C)
    static let muted = dynamic(light: 0x647069, dark: 0xA7B2AA)
    static let danger = dynamic(light: 0xD9564F, dark: 0xFF8077)
    static let surfaceVariant = dynamic(light: 0xE8ECE6, dark: 0x202922)
    static let outline = dynamic(light: 0xC9D0C9, dark: 0x445149)
    static let outlineVariant = dynamic(light: 0xE0E5DF, dark: 0x2C3730)
    static let cameraScrim = Color.black.opacity(0.72)

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

struct PpPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, 20)
            .foregroundStyle(isEnabled ? PpColor.ink : PpColor.muted)
            .background(
                isEnabled ? PpColor.signal.opacity(configuration.isPressed ? 0.76 : 1) : PpColor.surfaceVariant,
                in: RoundedRectangle(cornerRadius: 18)
            )
            .contentShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct PpSecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, 20)
            .foregroundStyle(isEnabled ? PpColor.forest : PpColor.muted)
            .background(
                PpColor.surface.opacity(configuration.isPressed ? 0.72 : 1),
                in: RoundedRectangle(cornerRadius: 18)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(isEnabled ? PpColor.outline : PpColor.outlineVariant)
            )
            .opacity(isEnabled ? 1 : 0.62)
            .contentShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct PpDangerButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, 20)
            .foregroundStyle(isEnabled ? PpColor.danger : PpColor.muted)
            .background(
                PpColor.danger.opacity(configuration.isPressed ? 0.22 : 0.13),
                in: RoundedRectangle(cornerRadius: 18)
            )
            .opacity(isEnabled ? 1 : 0.62)
    }
}

struct PpCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(PpColor.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(PpColor.outlineVariant))
    }
}

struct PpField: View {
    let title: LocalizedStringKey
    @Binding var text: String
    var keyboard: UIKeyboardType = .default
    var axis: Axis = .horizontal
    var isError = false

    var body: some View {
        TextField(title, text: $text, axis: axis)
            .textFieldStyle(.plain)
            .keyboardType(keyboard)
            .textInputAutocapitalization(keyboard == .emailAddress || keyboard == .URL ? .never : .sentences)
            .autocorrectionDisabled(keyboard == .emailAddress || keyboard == .URL)
            .padding(.horizontal, 14)
            .padding(.vertical, axis == .vertical ? 12 : 0)
            .frame(minHeight: 54)
            .background(PpColor.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isError ? PpColor.danger : PpColor.outline, lineWidth: isError ? 2 : 1)
            )
            .accessibilityLabel(title)
    }
}

struct PpBadge: View {
    let text: LocalizedStringKey
    var isComplete = false
    var isError = false

    var body: some View {
        Label(text, systemImage: isError ? "exclamationmark.triangle" : "checkmark.circle")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(isError ? PpColor.danger : PpColor.ink)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(
                isError ? PpColor.danger.opacity(0.13) : (isComplete ? PpColor.signal : PpColor.surfaceVariant),
                in: Capsule()
            )
    }
}

struct PpChoiceRow: View {
    let title: LocalizedStringKey
    var supportingText: String?
    let systemImage: String
    var selected = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .frame(width: 28)
                    .foregroundStyle(PpColor.forest)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(PpColor.ink)
                    if let supportingText, !supportingText.isEmpty {
                        Text(supportingText).font(.subheadline).foregroundStyle(PpColor.muted)
                    }
                }
                Spacer(minLength: 8)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? PpColor.forest : PpColor.outline)
            }
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(PpColor.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(selected ? PpColor.forest : PpColor.outlineVariant, lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

struct PpDisclosureRow: View {
    let title: LocalizedStringKey
    let value: String
    let systemImage: String
    var warning = false
    var complete = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: warning ? "exclamationmark.triangle" : systemImage)
                    .font(.title3)
                    .frame(width: 28)
                    .foregroundStyle(warning ? PpColor.danger : PpColor.forest)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(PpColor.ink)
                    Text(value)
                        .font(.subheadline)
                        .foregroundStyle(complete ? PpColor.muted : PpColor.danger)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").foregroundStyle(PpColor.muted)
            }
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .padding(16)
            .background(PpColor.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(PpColor.outlineVariant))
        }
        .buttonStyle(.plain)
    }
}

struct PpExpansionRow: View {
    let title: LocalizedStringKey
    var value: String?
    let systemImage: String
    let expanded: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .frame(width: 28)
                    .foregroundStyle(PpColor.forest)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(PpColor.ink)
                    if let value, !value.isEmpty {
                        Text(value).font(.subheadline).foregroundStyle(PpColor.muted)
                    }
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.down")
                    .foregroundStyle(PpColor.muted)
                    .rotationEffect(.degrees(expanded ? 180 : 0))
            }
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(PpColor.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
            .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .accessibilityValue(expanded ? Text("common.expanded") : Text("common.collapsed"))
    }
}

struct PpNotice: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(PpColor.danger.opacity(0.13), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct ReportPhotoThumbnail: View {
    let photoStore: PhotoStore
    let photo: ReportPhoto
    let size: CGFloat
    var crop: NormalizedPhotoRect?
    var cornerRadius: CGFloat = 12
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Rectangle().fill(PpColor.surfaceVariant)
                    .overlay(Image(systemName: "car.side").foregroundStyle(PpColor.muted))
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .task(id: photo.id) {
            guard let data = try? await photoStore.thumbnailData(
                for: photo,
                maximumPixelSize: max(1, Int(size * 3)),
                crop: crop
            ) else { return }
            image = UIImage(data: data)
        }
    }
}

struct ReportPhotoEvidence: View {
    let photoStore: PhotoStore
    let photo: ReportPhoto
    var crop: NormalizedPhotoRect?
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                Rectangle().fill(PpColor.surfaceVariant)
                    .overlay(Image(systemName: "car.side").foregroundStyle(PpColor.muted))
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 220)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityIdentifier("vehicle.plateEvidence")
        .task(id: loadID) {
            image = nil
            let croppedData = try? await photoStore.thumbnailData(
                for: photo,
                maximumPixelSize: 1_200,
                crop: crop
            )
            let data: Data?
            if let croppedData {
                data = croppedData
            } else {
                data = try? await photoStore.thumbnailData(
                    for: photo,
                    maximumPixelSize: 1_200
                )
            }
            image = data.flatMap(UIImage.init(data:))
        }
    }

    private var loadID: String {
        guard let crop else { return "\(photo.id):full" }
        return "\(photo.id):\(crop.left):\(crop.top):\(crop.right):\(crop.bottom)"
    }
}

extension View {
    func ppScreenBackground() -> some View {
        background(PpColor.canvas.ignoresSafeArea())
    }
}
