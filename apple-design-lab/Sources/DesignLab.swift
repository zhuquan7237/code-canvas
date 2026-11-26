import SwiftUI
import UIKit

@main
struct DesignLabApp: App {
    var body: some Scene {
        WindowGroup { LibraryScreen() }
    }
}

private let coral = Color(red: 0.88, green: 0.32, blue: 0.25)

enum Study: String, CaseIterable, Identifiable, Hashable {
    case coral, orbit, folded
    var id: String { rawValue }
    var title: String {
        switch self {
        case .coral: "Coral study"
        case .orbit: "Quiet orbit"
        case .folded: "Folded light"
        }
    }
    var subtitle: String {
        switch self {
        case .coral: "Color and contrast"
        case .orbit: "Shape and scale"
        case .folded: "Layers and alignment"
        }
    }
    var description: String {
        switch self {
        case .coral: "A coral ribbon curves across dark paper."
        case .orbit: "A pale orbit circles a cobalt center."
        case .folded: "Three folded sheets overlap under a warm light."
        }
    }
    var colors: (Color, Color, Color) {
        switch self {
        case .coral: (Color(red: 0.10, green: 0.12, blue: 0.19), coral, Color(red: 1, green: 0.80, blue: 0.63))
        case .orbit: (Color(red: 0.08, green: 0.14, blue: 0.25), Color(red: 0.30, green: 0.46, blue: 0.94), .white)
        case .folded: (Color(red: 0.24, green: 0.17, blue: 0.21), Color(red: 0.77, green: 0.39, blue: 0.52), Color(red: 1, green: 0.84, blue: 0.73))
        }
    }
}

struct LibraryScreen: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var route: [Study]
    @State private var showAbout = false
    @State private var query = ""

    init() {
        let fixture = ProcessInfo.processInfo.environment["DESIGN_LAB_OPEN_STUDY"]
        _route = State(initialValue: fixture == "1" ? [.coral] : [])
    }

    private var studies: [Study] {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return Study.allCases }
        return Study.allCases.filter { ($0.title + " " + $0.subtitle).localizedCaseInsensitiveContains(term) }
    }

    var body: some View {
        NavigationStack(path: $route) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("DESIGN LAB", systemImage: "paintbrush.pointed.fill")
                            .font(.caption.weight(.semibold))
                            .tracking(1.5)
                            .foregroundStyle(.secondary)
                        Text("A closer look").font(.title2.weight(.semibold)).accessibilityAddTraits(.isHeader)
                        Text("Explore three local studies in light, color, and space.")
                            .font(.body).foregroundStyle(.secondary)
                    }
                    if studies.isEmpty {
                        ContentUnavailableView.search(text: query)
                    } else {
                        LazyVGrid(
                            columns: [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 290 : 220), spacing: 16)],
                            alignment: .leading, spacing: 20
                        ) {
                            ForEach(studies) { study in
                                NavigationLink(value: study) { StudyCard(study: study) }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(study.title)
                                    .accessibilityHint(study.subtitle)
                            }
                        }
                    }
                }
                .frame(maxWidth: 1060, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Code Canvas")
            .searchable(text: $query, prompt: "Find a study")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { showAbout = true } label: {
                        Image(systemName: "info.circle").frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("About this design lab")
                }
            }
            .navigationDestination(for: Study.self) { StudyScreen(study: $0) }
            .sheet(isPresented: $showAbout) {
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            Text("A design reference, not a file editor.")
                                .font(.title2.weight(.semibold))
                            Text("The artwork is built in. The sample previews lighting and layout without importing files or connecting to the internet.")
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: 520, alignment: .leading)
                        .padding(24)
                        .frame(maxWidth: .infinity)
                    }
                    .navigationTitle("About")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Done") { showAbout = false }.frame(minHeight: 44)
                        }
                    }
                }
            }
        }
    }
}

private struct StudyCard: View {
    let study: Study
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Artwork(study: study)
                .aspectRatio(16.0 / 10.0, contentMode: .fit)
                .accessibilityHidden(true)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(study.title).font(.headline)
                    Text(study.subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary).accessibilityHidden(true)
            }
            .padding(14)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .contentShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct StudyScreen: View {
    let study: Study
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var zoom: Double = 1
    @State private var showGrid = false

    var body: some View {
        GeometryReader { geometry in
            let bounds = geometry.size
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ScrollView([.horizontal, .vertical]) {
                        ZStack {
                            Artwork(study: study)
                            if showGrid { AlignmentGrid().accessibilityHidden(true) }
                        }
                        .frame(width: stageWidth(bounds) * CGFloat(zoom), height: stageWidth(bounds) * 0.625 * CGFloat(zoom))
                        .accessibilityLabel(study.description)
                        .accessibilityAddTraits(.isImage)
                    }
                    .defaultScrollAnchor(.center)
                    .scrollBounceBehavior(.basedOnSize)
                    .frame(height: stageWidth(bounds) * 0.625)
                    .background(study.colors.0)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    Text(study.subtitle)
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .frame(width: stageWidth(bounds), alignment: .leading)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemGroupedBackground))
        }
        .navigationTitle(study.title)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom, spacing: 8) {
            controls
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
        }
    }

    private func stageWidth(_ size: CGSize) -> CGFloat {
        max(1, min(size.width - 40, min(960, size.height * 1.6)))
    }

    private var controls: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 4) { zoomControls; gridControl }
                .fixedSize(horizontal: true, vertical: false)
            VStack(spacing: 4) { HStack(spacing: 4) { zoomControls }; gridControl }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .foregroundStyle(.primary)
        .modifier(ControlMaterial(opaque: reduceTransparency))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Canvas controls")
    }

    @ViewBuilder private var zoomControls: some View {
        Button { changeZoom(max(1, zoom - 0.25)) } label: {
            Image(systemName: "minus").frame(minWidth: 44, minHeight: 44)
        }.accessibilityLabel("Zoom out").disabled(zoom <= 1)
        Button { changeZoom(1) } label: {
            Text(zoom.formatted(.percent.precision(.fractionLength(0))))
                .font(.callout.monospacedDigit()).padding(.horizontal, 4)
                .frame(minWidth: 44, minHeight: 44)
        }.accessibilityLabel("Fit artwork")
            .accessibilityValue(zoom.formatted(.percent.precision(.fractionLength(0))))
        Button { changeZoom(min(2, zoom + 0.25)) } label: {
            Image(systemName: "plus").frame(minWidth: 44, minHeight: 44)
        }.accessibilityLabel("Zoom in").disabled(zoom >= 2)
    }

    private var gridControl: some View {
        Button {
            showGrid.toggle()
        } label: {
            Image(systemName: showGrid ? "square.grid.3x3.fill" : "square.grid.3x3")
                .frame(minWidth: 44, minHeight: 44)
        }
        .accessibilityLabel("Alignment grid")
        .accessibilityValue(showGrid ? "On" : "Off")
        .accessibilityAddTraits(showGrid ? .isSelected : [])
    }

    private func changeZoom(_ value: Double) {
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.18)) { zoom = value }
    }
}

private struct ControlMaterial: ViewModifier {
    let opaque: Bool
    func body(content: Content) -> some View {
        if opaque {
            content.background(Color(uiColor: .secondarySystemBackground), in: .capsule)
                .overlay { Capsule().strokeBorder(Color(uiColor: .separator)) }
        } else {
            content.glassEffect(.regular, in: .capsule)
        }
    }
}

private struct AlignmentGrid: View {
    var body: some View {
        Canvas { context, size in
            var lines = Path()
            for i in 1...2 {
                let x = size.width * CGFloat(i) / 3
                let y = size.height * CGFloat(i) / 3
                lines.move(to: CGPoint(x: x, y: 0)); lines.addLine(to: CGPoint(x: x, y: size.height))
                lines.move(to: CGPoint(x: 0, y: y)); lines.addLine(to: CGPoint(x: size.width, y: y))
            }
            context.stroke(lines, with: .color(.black.opacity(0.75)), lineWidth: 2)
            context.stroke(lines, with: .color(.white.opacity(0.9)), lineWidth: 0.7)
        }.allowsHitTesting(false)
    }
}

private struct Artwork: View {
    let study: Study
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let (ground, ink, light) = study.colors
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: w * x, y: h * y) }
            let bounds = CGRect(origin: .zero, size: size)
            ctx.fill(Path(bounds), with: .radialGradient(
                Gradient(colors: [ink.opacity(0.56), ground]),
                center: p(0.27, 0.16), startRadius: 0, endRadius: w * 0.78))
            switch study {
            case .coral:
                for i in 0..<14 {
                    let f = CGFloat(i) / 13
                    var ribbon = Path()
                    ribbon.move(to: p(0.17 + f * 0.24, 0.77 + f * 0.10))
                    ribbon.addCurve(to: p(0.83 - f * 0.20, 0.24 - f * 0.04),
                                    control1: p(0.08 + f * 0.37, 0.10 + f * 0.60),
                                    control2: p(0.77 + f * 0.14, 0.81 - f * 0.53))
                    ctx.stroke(ribbon, with: .linearGradient(
                        Gradient(colors: [light, ink, light.opacity(0.65)]),
                        startPoint: p(0.15, 0.06), endPoint: p(0.86, 0.82)),
                        style: StrokeStyle(lineWidth: max(1, w * 0.013), lineCap: .round))
                }
            case .orbit:
                let center = p(0.5, 0.5), radius = h * 0.23
                let planet = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
                ctx.fill(Path(ellipseIn: planet), with: .radialGradient(
                    Gradient(colors: [light, ink, ground]), center: p(0.46, 0.38),
                    startRadius: 0, endRadius: radius * 1.6))
                for i in 1...4 {
                    let f = CGFloat(i)
                    let ring = CGRect(x: w * (0.26 - f * 0.022), y: h * (0.39 - f * 0.028),
                                      width: w * (0.48 + f * 0.044), height: h * (0.22 + f * 0.056))
                    ctx.stroke(Path(ellipseIn: ring), with: .color(light.opacity(0.70 - Double(i) * 0.09)),
                               lineWidth: max(1, w * 0.002))
                }
            case .folded:
                for i in 0..<3 {
                    let f = CGFloat(i) * 0.12
                    var sheet = Path()
                    sheet.move(to: p(0.18 + f, 0.67 + f * 0.66))
                    sheet.addLine(to: p(0.35 + f, 0.22 + f * 0.29))
                    sheet.addLine(to: p(0.60 + f, 0.24 + f * 0.72))
                    sheet.addLine(to: p(0.54 + f, 0.58 + f * 0.85))
                    sheet.closeSubpath()
                    ctx.fill(sheet, with: .linearGradient(
                        Gradient(colors: [light, ink, ground]), startPoint: p(0.29 + f, 0.14),
                        endPoint: p(0.75 + f, 0.9)))
                    ctx.stroke(sheet, with: .color(light.opacity(0.48)), lineWidth: max(1, w * 0.002))
                }
            }
        }
    }
}
