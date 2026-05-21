import SwiftUI

struct SearchView: View {
    @ObservedObject var model: ZoneExplorerModel

    var body: some View {
        VStack(spacing: 0) {
            searchField
                .padding()

            if model.zones.isEmpty && !model.isLoading {
                ContentUnavailableView(
                    "검색 결과 없음",
                    systemImage: "magnifyingglass",
                    description: Text("다른 장소명이나 주소로 다시 검색해보세요.")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(model.zones) { zone in
                    Button {
                        model.select(zone)
                    } label: {
                        ZoneRow(zone: zone)
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("검색")
        .sheet(item: $model.selectedZone) { zone in
            ZoneDetailView(zone: zone, model: model)
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            TextField("예: 강남, 시청, 공원", text: $model.query)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.search)
                .onSubmit {
                    Task {
                        await model.search()
                    }
                }

            Button {
                Task {
                    await model.search()
                }
            } label: {
                if model.isLoading {
                    ProgressView()
                } else {
                    Image(systemName: "magnifyingglass")
                }
            }
            .buttonStyle(.borderedProminent)
            .accessibilityLabel("검색")
        }
    }
}
