import SwiftUI

struct ZoneListView: View {
    @ObservedObject var model: ZoneExplorerModel

    var body: some View {
        List {
            if model.isLoading {
                ProgressView("불러오는 중")
            }

            ForEach(model.zones) { zone in
                Button {
                    model.select(zone)
                } label: {
                    ZoneRow(zone: zone)
                }
                .buttonStyle(.plain)
            }
        }
        .navigationTitle("흡연구역")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task {
                        await model.loadInitialZones()
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel("새로고침")
            }
        }
        .sheet(item: $model.selectedZone) { zone in
            ZoneDetailView(zone: zone, model: model)
        }
    }
}

struct ZoneRow: View {
    let zone: SmokingZone

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "mappin.circle.fill")
                .font(.title2)
                .foregroundStyle(.green)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 5) {
                Text(zone.title)
                    .font(.headline)
                    .foregroundStyle(.primary)

                if !zone.summary.isEmpty {
                    Text(zone.summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Text(zone.address)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            Spacer()
        }
        .padding(.vertical, 6)
    }
}
