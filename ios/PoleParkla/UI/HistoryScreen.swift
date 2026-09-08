import SwiftUI

struct HistoryScreen: View {
    @Bindable var model: AppModel
    @State private var pendingDeletion: Report?

    var body: some View {
        Group {
            if model.reports.isEmpty {
                ContentUnavailableView {
                    Label("history.empty.title", systemImage: "car.side")
                } description: {
                    Text("history.empty.body")
                } actions: {
                    Button("history.new") { model.startNewCameraSession() }
                        .buttonStyle(PpPrimaryButtonStyle())
                }
                .padding(20)
            } else {
                List {
                    ForEach(model.reports) { report in
                        HStack(spacing: 10) {
                            Button { open(report) } label: { reportRow(report) }
                                .buttonStyle(.plain)
                                .accessibilityLabel(accessibilityLabel(for: report))
                                .accessibilityIdentifier("history.report.\(report.id)")
                            Menu {
                                Button("common.open") { open(report) }
                                Button("common.delete", role: .destructive) { pendingDeletion = report }
                            } label: {
                                Image(systemName: "ellipsis.circle")
                                    .font(.title3)
                                    .frame(width: 48, height: 48)
                            }
                            .accessibilityLabel("history.actions")
                            .accessibilityIdentifier("history.report.menu.\(report.id)")
                        }
                        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 8))
                        .listRowSeparator(.hidden)
                        .listRowBackground(PpColor.surface)
                        .onAppear { model.loadMoreReports(after: report.id) }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("history.title")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { model.startNewCameraSession() } label: { Image(systemName: "plus") }
                    .accessibilityLabel("history.new")
            }
        }
        .confirmationDialog(
            "history.delete.confirm.title",
            isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
            titleVisibility: .visible
        ) {
            Button("common.delete", role: .destructive) {
                guard let pendingDeletion else { return }
                Task {
                    await model.deleteReport(pendingDeletion)
                    self.pendingDeletion = nil
                }
            }
            Button("common.cancel", role: .cancel) { pendingDeletion = nil }
        } message: { Text("history.delete.confirm.body") }
        .ppScreenBackground()
    }

    private func reportRow(_ report: Report) -> some View {
        HStack(spacing: 14) {
            reportThumbnail(report)
            VStack(alignment: .leading, spacing: 5) {
                HStack(alignment: .firstTextBaseline) {
                    Text(report.plate.isEmpty ? model.localized("history.unconfirmed") : report.plate)
                        .font(.headline)
                        .foregroundStyle(PpColor.ink)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    statusBadge(report.status)
                }
                Text(report.address.isEmpty ? model.localized("history.location.pending") : report.address)
                    .font(.subheadline)
                    .foregroundStyle(PpColor.muted)
                    .lineLimit(2)
                Text(report.occurredAt.formatted(
                    Date.FormatStyle(date: .abbreviated, time: .shortened).locale(model.settings.value.locale)
                ))
                .font(.caption)
                .foregroundStyle(PpColor.muted)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
    }

    @ViewBuilder
    private func reportThumbnail(_ report: Report) -> some View {
        if let photo = report.primaryPhoto {
            ReportPhotoThumbnail(photoStore: model.photoStore, photo: photo, size: 72, cornerRadius: 14)
        } else {
            RoundedRectangle(cornerRadius: 14)
                .fill(PpColor.surfaceVariant)
                .frame(width: 72, height: 72)
                .overlay(Image(systemName: "car.side").foregroundStyle(PpColor.muted))
        }
    }

    private func statusBadge(_ status: ReportStatus) -> some View {
        Text(statusText(status))
            .font(.caption.weight(.semibold))
            .foregroundStyle(PpColor.ink)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(status == .draft ? PpColor.surfaceVariant : PpColor.signal, in: Capsule())
    }

    private func open(_ report: Report) {
        model.selectedReportID = report.id
    }

    private func accessibilityLabel(for report: Report) -> String {
        let plate = report.plate.isEmpty ? model.localized("history.unconfirmed") : report.plate
        let address = report.address.isEmpty ? model.localized("history.location.pending") : report.address
        let date = report.occurredAt.formatted(
            Date.FormatStyle(date: .abbreviated, time: .omitted).locale(model.settings.value.locale)
        )
        return [plate, address, date, model.localized(statusKey(report.status))].joined(separator: ", ")
    }

    private func statusText(_ status: ReportStatus) -> LocalizedStringKey {
        switch status {
        case .draft: "status.draft"
        case .ready: "status.ready"
        case .handedOffToMail: "status.handed_off"
        }
    }

    private func statusKey(_ status: ReportStatus) -> String {
        switch status {
        case .draft: "status.draft"
        case .ready: "status.ready"
        case .handedOffToMail: "status.handed_off"
        }
    }
}
