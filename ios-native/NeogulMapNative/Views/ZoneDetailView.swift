import SwiftUI

struct ZoneDetailView: View {
    let zone: SmokingZone
    @ObservedObject var model: ZoneExplorerModel
    @Environment(\.openURL) private var openURL
    @State private var reviewText = ""
    @State private var isSubmittingReview = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                handle

                VStack(alignment: .leading, spacing: 7) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(zone.title)
                            .font(.system(size: 22, weight: .black))
                            .foregroundStyle(Color.nugulPrimary)
                            .lineLimit(2)

                        TypeBadge(zone: zone)
                    }

                    Text(zone.address)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineLimit(2)
                }

                if let imageURL = model.imageURL(for: zone) {
                    AsyncImage(url: imageURL) { phase in
                        switch phase {
                        case let .success(image):
                            image
                                .resizable()
                                .scaledToFill()
                        case .failure:
                            placeholderImage
                        case .empty:
                            ZStack {
                                Color.nugulMuted
                                ProgressView()
                            }
                        @unknown default:
                            placeholderImage
                        }
                    }
                    .frame(height: 210)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Color.nugulBorder, lineWidth: 1)
                    )
                }

                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "mappin")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.nugulMutedText.opacity(0.72))
                        .frame(width: 24)

                    Text(zone.description.isEmpty ? "등록된 상세 설명이 없습니다." : zone.description)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineSpacing(3)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.nugulMuted, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                reviewsSection

                Button {
                    openDirections()
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "location.north.fill")
                            .font(.system(size: 18, weight: .bold))
                        Text("길찾기 시작")
                            .font(.system(size: 18, weight: .black))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .foregroundStyle(.white)
                    .background(Color.nugulPrimary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 7)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
        .background(Color.white)
        .task(id: zone.id) {
            await model.loadReviews(for: zone)
        }
    }

    private var handle: some View {
        Capsule()
            .fill(Color.nugulBorder)
            .frame(width: 48, height: 6)
            .frame(maxWidth: .infinity)
            .padding(.top, 16)
    }

    private var placeholderImage: some View {
        ZStack {
            Color.nugulMuted
            Image(systemName: "photo")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(Color.nugulMutedText.opacity(0.45))
        }
    }

    private var reviewsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("리뷰")
                    .font(.system(size: 16, weight: .black))
                    .foregroundStyle(Color.nugulPrimary)
                Spacer()
                Text("\(model.reviews(for: zone).count)")
                    .font(.system(size: 12, weight: .black))
                    .foregroundStyle(Color.nugulMutedText)
            }

            if model.loadingReviewZoneID == zone.id {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("리뷰 불러오는 중")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color.nugulMutedText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else if model.reviews(for: zone).isEmpty {
                Text("아직 등록된 리뷰가 없습니다.")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(spacing: 10) {
                    ForEach(model.reviews(for: zone)) { review in
                        ZoneReviewRow(review: review)
                    }
                }
            }

            reviewComposer
        }
        .padding(16)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(Color.nugulBorder, lineWidth: 1)
        )
    }

    private var reviewComposer: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(model.currentUser == nil ? "로그인 후 리뷰를 남길 수 있어요." : "리뷰 남기기")
                .font(.system(size: 13, weight: .black))
                .foregroundStyle(Color.nugulMutedText)

            TextField("이 장소는 어땠나요?", text: $reviewText, axis: .vertical)
                .lineLimit(2...4)
                .font(.system(size: 14, weight: .medium))
                .padding(12)
                .background(Color.nugulMuted, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .disabled(model.currentUser == nil || isSubmittingReview)

            Button {
                submitReview()
            } label: {
                HStack {
                    if isSubmittingReview {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(model.currentUser == nil ? "로그인이 필요합니다" : "리뷰 등록")
                        .font(.system(size: 14, weight: .black))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .foregroundStyle(.white)
                .background(
                    reviewSubmitDisabled ? Color.nugulMutedText.opacity(0.35) : Color.nugulPrimary,
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
            }
            .buttonStyle(.plain)
            .disabled(reviewSubmitDisabled)
        }
        .padding(.top, 4)
    }

    private var reviewSubmitDisabled: Bool {
        model.currentUser == nil
            || isSubmittingReview
            || reviewText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func openDirections() {
        let encodedName = zone.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "NugulMap"
        let urlString = "http://maps.apple.com/?daddr=\(zone.latitude),\(zone.longitude)&q=\(encodedName)"
        guard let url = URL(string: urlString) else {
            return
        }

        openURL(url)
    }

    private func submitReview() {
        guard !reviewSubmitDisabled else {
            return
        }

        isSubmittingReview = true
        Task {
            let didSubmit = await model.submitReview(for: zone, content: reviewText)
            if didSubmit {
                reviewText = ""
            }
            isSubmittingReview = false
        }
    }
}

private struct ZoneReviewRow: View {
    let review: ZoneReview

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(Color.nugulPrimary.opacity(0.1))
                .frame(width: 34, height: 34)
                .overlay {
                    Text(initial)
                        .font(.system(size: 13, weight: .black))
                        .foregroundStyle(Color.nugulPrimary)
                }

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(review.authorNickname)
                        .font(.system(size: 13, weight: .black))
                        .foregroundStyle(Color.nugulPrimary)
                    Text(String(review.createdAt.prefix(10)))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.nugulMutedText.opacity(0.72))
                }

                Text(review.content.isEmpty ? "내용 없는 리뷰" : review.content)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.nugulMutedText)
                    .lineSpacing(2)
            }

            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Color.nugulMuted.opacity(0.7), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var initial: String {
        review.authorNickname.first.map { String($0) } ?? "?"
    }
}

private struct TypeBadge: View {
    let zone: SmokingZone

    var body: some View {
        Text(label)
            .font(.system(size: 10, weight: .black))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(background, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
    }

    private var label: String {
        if zone.type.contains("INDOOR") || zone.type.contains("실내") {
            return "실내"
        }

        if zone.type.contains("BOOTH") || zone.type.contains("부스") {
            return "부스"
        }

        return "개방형"
    }

    private var foreground: Color {
        if label == "실내" {
            return .blue
        }

        if label == "부스" {
            return .green
        }

        return .orange
    }

    private var background: Color {
        foreground.opacity(0.1)
    }
}

private extension Color {
    static let nugulPrimary = Color(red: 0.09, green: 0.09, blue: 0.09)
    static let nugulMuted = Color(red: 0.96, green: 0.96, blue: 0.96)
    static let nugulMutedText = Color(red: 0.32, green: 0.32, blue: 0.32)
    static let nugulBorder = Color(red: 0.90, green: 0.90, blue: 0.90)
}
