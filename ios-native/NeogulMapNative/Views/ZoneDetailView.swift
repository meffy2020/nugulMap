import SwiftUI

struct ZoneDetailView: View {
    let zone: SmokingZone
    @ObservedObject var model: ZoneExplorerModel
    @Environment(\.openURL) private var openURL
    @State private var reviewText = ""
    @State private var isSubmittingReview = false
    @State private var isReviewPolicyAccepted = false
    @State private var reportTarget: ZoneReview?
    @State private var blockTarget: ZoneReview?
    @State private var isModeratingReview = false
    @State private var showsZoneReportDialog = false
    @State private var isReportingZone = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                handle

                VStack(alignment: .leading, spacing: 7) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(zone.title)
                            .font(.title2.weight(.black))
                            .foregroundStyle(Color.nugulPrimary)
                            .fixedSize(horizontal: false, vertical: true)

                        TypeBadge(zone: zone)
                    }

                    Text(zone.address)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.nugulMutedText)
                        .fixedSize(horizontal: false, vertical: true)
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
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.nugulMutedText)
                        .lineSpacing(3)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.nugulMuted, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                zoneReportControl

                reviewsSection

                Button {
                    openDirections()
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "location.north.fill")
                            .font(.system(size: 18, weight: .bold))
                        Text("길찾기 시작")
                            .font(.headline.weight(.black))
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
        .confirmationDialog(
            "리뷰 신고",
            isPresented: reportDialogPresented,
            titleVisibility: .visible
        ) {
            if let reportTarget {
                Button("불쾌하거나 공격적인 내용") {
                    submitReport(reportTarget, reason: "OFFENSIVE")
                }
                Button("괴롭힘 또는 혐오 표현") {
                    submitReport(reportTarget, reason: "HARASSMENT")
                }
                Button("스팸 또는 홍보") {
                    submitReport(reportTarget, reason: "SPAM")
                }
                Button("개인정보 노출") {
                    submitReport(reportTarget, reason: "PERSONAL_INFORMATION")
                }
            }
            Button("취소", role: .cancel) {}
        } message: {
            Text("신고 사유를 선택하면 운영팀에 검토 요청이 접수됩니다.")
        }
        .alert("사용자 차단", isPresented: blockDialogPresented, presenting: blockTarget) { review in
            Button("차단", role: .destructive) {
                submitBlock(review)
            }
            Button("취소", role: .cancel) {}
        } message: { review in
            Text("\(review.authorNickname)님의 리뷰를 앞으로 표시하지 않습니다.")
        }
    }

    private var reportDialogPresented: Binding<Bool> {
        Binding(
            get: { reportTarget != nil },
            set: { isPresented in
                if !isPresented {
                    reportTarget = nil
                }
            }
        )
    }

    private var blockDialogPresented: Binding<Bool> {
        Binding(
            get: { blockTarget != nil },
            set: { isPresented in
                if !isPresented {
                    blockTarget = nil
                }
            }
        )
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

    private var zoneReportControl: some View {
        VStack(alignment: .leading, spacing: 5) {
            Button {
                showsZoneReportDialog = true
            } label: {
                HStack(spacing: 7) {
                    if isReportingZone {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "exclamationmark.bubble")
                    }
                    Text("장소 신고")
                }
                .font(.footnote.weight(.black))
                .foregroundStyle(Color.nugulDanger)
                .frame(minWidth: 100, minHeight: 44)
                .padding(.horizontal, 8)
                .background(Color.nugulDanger.opacity(0.07), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isReportingZone)
            .accessibilityLabel("장소 또는 사진 신고")
            .accessibilityHint("신고 사유를 선택합니다")
            .confirmationDialog(
                "장소 신고",
                isPresented: $showsZoneReportDialog,
                titleVisibility: .visible
            ) {
                Button("위치 또는 정보가 잘못됨") {
                    submitZoneReport(reason: "INACCURATE")
                }
                Button("중복으로 등록됨") {
                    submitZoneReport(reason: "DUPLICATE")
                }
                Button("폐쇄되었거나 존재하지 않음") {
                    submitZoneReport(reason: "CLOSED")
                }
                Button("부적절한 사진 또는 내용") {
                    submitZoneReport(reason: "OFFENSIVE")
                }
                Button("개인정보 노출") {
                    submitZoneReport(reason: "PERSONAL_INFORMATION")
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text("운영팀이 장소 정보와 첨부 사진을 검토합니다.")
            }

            if let message = model.zoneModerationMessage, !message.isEmpty {
                Text(message)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var reviewsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("리뷰")
                    .font(.headline.weight(.black))
                    .foregroundStyle(Color.nugulPrimary)
                Spacer()
                Text("\(model.reviews(for: zone).count)")
                    .font(.caption.weight(.black))
                    .foregroundStyle(Color.nugulMutedText)
            }

            if model.loadingReviewZoneID == zone.id {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("리뷰 불러오는 중")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(Color.nugulMutedText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else if let message = model.reviewLoadErrorByZoneID[zone.id] {
                Text(message)
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(Color.orange)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else if model.reviews(for: zone).isEmpty {
                Text("아직 등록된 리뷰가 없습니다.")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(spacing: 10) {
                    ForEach(model.reviews(for: zone)) { review in
                        ZoneReviewRow(
                            review: review,
                            canReport: review.authorId == nil || review.authorId != model.currentUser?.id,
                            canBlock: review.authorId != nil && review.authorId != model.currentUser?.id,
                            isSignedIn: model.currentUser != nil,
                            isModerating: isModeratingReview,
                            onReport: { reportTarget = review },
                            onBlock: { blockTarget = review }
                        )
                    }
                }
            }

            if let message = model.reviewModerationMessage, !message.isEmpty {
                Text(message)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .frame(maxWidth: .infinity, alignment: .leading)
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
                .font(.footnote.weight(.black))
                .foregroundStyle(Color.nugulMutedText)

            if model.currentUser != nil {
                Text("모두가 보는 리뷰예요. 개인정보, 욕설, 광고는 등록할 수 없습니다.")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Color.nugulMutedText.opacity(0.82))
                    .fixedSize(horizontal: false, vertical: true)

                UgcPolicyConsentRow(isAccepted: $isReviewPolicyAccepted)
            }

            TextField("이 장소는 어땠나요?", text: $reviewText, axis: .vertical)
                .lineLimit(2...4)
                .font(.subheadline.weight(.medium))
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
                        .font(.subheadline.weight(.black))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .foregroundStyle(.white)
                .background(
                    reviewSubmitDisabled ? Color.nugulMutedText : Color.nugulPrimary,
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
            }
            .buttonStyle(.plain)
            .allowsHitTesting(!reviewSubmitDisabled)
            .accessibilityValue(reviewSubmitDisabled ? "비활성화" : "활성화")
        }
        .padding(.top, 4)
    }

    private var reviewSubmitDisabled: Bool {
        model.currentUser == nil
            || isSubmittingReview
            || !isReviewPolicyAccepted
            || reviewText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func openDirections() {
        let encodedName = zone.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "NugulMap"
        let urlString = "https://maps.apple.com/?daddr=\(zone.latitude),\(zone.longitude)&q=\(encodedName)"
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
                isReviewPolicyAccepted = false
            }
            isSubmittingReview = false
        }
    }

    private func submitZoneReport(reason: String) {
        guard !isReportingZone else {
            return
        }
        isReportingZone = true
        Task {
            _ = await model.reportZone(zone, reason: reason)
            isReportingZone = false
        }
    }

    private func submitReport(_ review: ZoneReview, reason: String) {
        guard !isModeratingReview else {
            return
        }
        isModeratingReview = true
        Task {
            _ = await model.reportReview(review, in: zone, reason: reason)
            isModeratingReview = false
        }
    }

    private func submitBlock(_ review: ZoneReview) {
        guard !isModeratingReview else {
            return
        }
        isModeratingReview = true
        Task {
            _ = await model.blockReviewAuthor(review)
            isModeratingReview = false
        }
    }
}

struct UgcPolicyConsentRow: View {
    @Binding var isAccepted: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Button {
                isAccepted.toggle()
            } label: {
                Image(systemName: isAccepted ? "checkmark.square.fill" : "square")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(isAccepted ? Color.nugulPrimary : Color.nugulMutedText)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .contentShape(Rectangle())
            .accessibilityLabel("필수 약관 및 커뮤니티 운영정책 동의")
            .accessibilityValue(isAccepted ? "동의함" : "동의하지 않음")

            VStack(alignment: .leading, spacing: 3) {
                Text("필수 · 이용약관 및 커뮤니티 운영정책에 동의합니다.")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color.nugulMutedText)
                    .fixedSize(horizontal: false, vertical: true)

                Link("약관·운영정책 보기", destination: AppConfig.termsURL)
                    .font(.caption.weight(.black))
                    .foregroundStyle(Color.black)
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                    .tint(Color.black)
                    .buttonStyle(.plain)
            }
            .padding(.top, 5)

            Spacer(minLength: 0)
        }
    }
}

private struct ZoneReviewRow: View {
    let review: ZoneReview
    let canReport: Bool
    let canBlock: Bool
    let isSignedIn: Bool
    let isModerating: Bool
    let onReport: () -> Void
    let onBlock: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(Color.nugulPrimary.opacity(0.1))
                .frame(width: 34, height: 34)
                .overlay {
                    Text(initial)
                        .font(.footnote.weight(.black))
                        .foregroundStyle(Color.nugulPrimary)
                }

            VStack(alignment: .leading, spacing: 4) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(review.authorNickname)
                        .font(.footnote.weight(.black))
                        .foregroundStyle(Color.nugulPrimary)
                    Text(String(review.createdAt.prefix(10)))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.nugulMutedText)
                }

                Text(review.content.isEmpty ? "내용 없는 리뷰" : review.content)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.nugulMutedText)
                    .lineSpacing(2)

                if canReport || canBlock {
                    HStack(spacing: 4) {
                        Spacer(minLength: 0)
                        if canReport {
                            Button(action: onReport) {
                                Text("신고")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(Color.nugulMutedText)
                                    .frame(minWidth: 44, minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                                .buttonStyle(.plain)
                                .disabled(!isSignedIn || isModerating)
                        }
                        if canBlock {
                            Button(role: .destructive, action: onBlock) {
                                Text("차단")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(Color.nugulDanger)
                                    .frame(minWidth: 44, minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                                .buttonStyle(.plain)
                                .disabled(!isSignedIn || isModerating)
                        }
                    }
                }
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
            .font(.caption2.weight(.black))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(background, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
            .fixedSize(horizontal: true, vertical: true)
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
            return Color(red: 0.04, green: 0.20, blue: 0.52)
        }

        if label == "부스" {
            return Color(red: 0.03, green: 0.31, blue: 0.15)
        }

        return Color(red: 0.42, green: 0.19, blue: 0.02)
    }

    private var background: Color {
        foreground.opacity(0.1)
    }
}

private extension Color {
    static let nugulPrimary = Color(red: 0.09, green: 0.09, blue: 0.09)
    static let nugulMuted = Color(red: 0.96, green: 0.96, blue: 0.96)
    static let nugulMutedText = Color(red: 0.32, green: 0.32, blue: 0.32)
    static let nugulDanger = Color(red: 0.62, green: 0.03, blue: 0.06)
    static let nugulBorder = Color(red: 0.90, green: 0.90, blue: 0.90)
}
