@preconcurrency import Intents
@preconcurrency import MessageUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

enum MailComposerResult: Equatable, Sendable {
    case cancelled
    case saved
    case sent
    case failed

    var countsAsHandoff: Bool { self != .failed }
}

struct MailDraftPayload: Identifiable {
    var id = UUID()
    var reportID: String
    var recipient: String
    var subject: String
    var body: String
    var attachments: [URL]
}

struct MailComposerView: UIViewControllerRepresentable {
    let payload: MailDraftPayload
    let completion: @MainActor (MailComposerResult) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(completion: completion) }

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let composer = MFMailComposeViewController()
        composer.mailComposeDelegate = context.coordinator
        composer.setToRecipients([payload.recipient])
        composer.setSubject(payload.subject)
        composer.setMessageBody(payload.body, isHTML: false)
        for attachment in payload.attachments {
            if let data = try? Data(contentsOf: attachment) {
                let mimeType = UTType(filenameExtension: attachment.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
                composer.addAttachmentData(data, mimeType: mimeType, fileName: attachment.lastPathComponent)
            }
        }
        return composer
    }

    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {}

    @MainActor
    final class Coordinator: NSObject, @preconcurrency MFMailComposeViewControllerDelegate {
        let completion: @MainActor (MailComposerResult) -> Void
        init(completion: @escaping @MainActor (MailComposerResult) -> Void) { self.completion = completion }

        func mailComposeController(
            _ controller: MFMailComposeViewController,
            didFinishWith result: MFMailComposeResult,
            error: Error?
        ) {
            let mapped: MailComposerResult
            if error != nil || result == .failed {
                mapped = .failed
            } else {
                mapped = switch result {
                case .cancelled: .cancelled
                case .saved: .saved
                case .sent: .sent
                case .failed: .failed
                @unknown default: .failed
                }
            }
            controller.dismiss(animated: true) { Task { @MainActor in self.completion(mapped) } }
        }
    }
}

struct ShareSheetView: UIViewControllerRepresentable {
    let payload: MailDraftPayload
    let completion: @MainActor () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(completion: completion) }

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let source = MailShareItem(payload: payload)
        let controller = UIActivityViewController(
            activityItems: [source] + payload.attachments,
            applicationActivities: nil
        )
        controller.completionWithItemsHandler = { _, _, _, _ in
            Task { @MainActor in context.coordinator.completion() }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}

    final class Coordinator {
        let completion: @MainActor () -> Void
        init(completion: @escaping @MainActor () -> Void) { self.completion = completion }
    }
}

private final class MailShareItem: NSObject, UIActivityItemSource {
    let payload: MailDraftPayload
    init(payload: MailDraftPayload) { self.payload = payload }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        payload.body
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        itemForActivityType activityType: UIActivity.ActivityType?
    ) -> Any? {
        payload.body
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        subjectForActivityType activityType: UIActivity.ActivityType?
    ) -> String {
        payload.subject
    }

    func activityViewControllerShareRecipients(_ activityViewController: UIActivityViewController) -> [INPerson] {
        let handle = INPersonHandle(value: payload.recipient, type: .emailAddress)
        return [INPerson(personHandle: handle, nameComponents: nil, displayName: payload.recipient, image: nil, contactIdentifier: nil, customIdentifier: nil)]
    }
}
