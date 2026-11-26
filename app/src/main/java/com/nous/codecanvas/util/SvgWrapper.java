package com.nous.codecanvas.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The page an SVG is previewed in.
 *
 * <p>Two responsibilities, both learned from real defects:</p>
 * <ol>
 *   <li><b>Nothing is cropped.</b> An artwork authored for a full screen usually carries
 *       {@code preserveAspectRatio="…slice"} plus {@code width:100vw;height:100vh}. {@code slice}
 *       means "crop the overflow to fill the viewport", which on a portrait phone cuts a wide
 *       drawing down to its middle band. Any {@code slice} is rewritten to {@code meet} and the
 *       SVG viewport is sized uniformly to its live viewBox.</li>
 *   <li><b>Zooming stays vector and costs nothing per frame.</b> The page exposes
 *       {@code CCViewport.set(scale, dx, dy)}, which writes one CSS transform on the wrapper. The
 *       SVG is parsed once; a pinch changes a transform, not a document.</li>
 * </ol>
 *
 * <p>Links inside the artwork are neutralised: this surface owns pan and pinch, so a drag must
 * never navigate. Local files cannot reach the app either — the WebView has no JS bridge, no file
 * access and no network unless the user explicitly allows it.</p>
 */
public final class SvgWrapper {

    private static final Pattern ROOT_SVG_PATTERN = Pattern.compile("<svg(?=[\\s/>])((?:\"[^\"]*\"|'[^']*'|[^\"'>]+)*+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESERVE_ASPECT_PATTERN =
            Pattern.compile("preserveAspectRatio\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESERVE_ASPECT_APOS_PATTERN =
            Pattern.compile("preserveAspectRatio\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);

    private SvgWrapper() {
    }

    /**
     * @param svgContent the document's source
     * @param darkTheme  page background follows the app theme
     */
    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme) {
        return wrapSvgInResponsiveHtml(svgContent, darkTheme, null);
    }

    /**
     * @param viewBoxOverride a preview-only viewBox (from "fit to content"), or null to keep the
     *                        document's own. Never written back to the file.
     */
    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme, String viewBoxOverride) {
        return wrapSvgInResponsiveHtml(svgContent, darkTheme, viewBoxOverride, true);
    }

    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme,
                                                String viewBoxOverride, boolean allowScripts) {
        String bg = darkTheme ? "#121417" : "#F8FAFC";
        String grid = darkTheme ? "#22262c" : "#ecf0f3";
        String svg = nativeSvg(svgContent, viewBoxOverride);
        // A real SVG document (not inline HTML) is essential: SVG scripts use
        // document.documentElement, root CSS, CDATA and document-relative fragment links.
        String bytes = java.util.Base64.getEncoder().encodeToString(
                svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:" + bg
                + ";background-image:linear-gradient(" + grid + " 1px,transparent 1px),"
                + "linear-gradient(90deg," + grid + " 1px,transparent 1px);background-size:20px 20px}"
                + "#cc-stage{position:absolute;inset:0;transform-origin:0 0}"
                + "#cc-fit{border:0;display:block;width:100%;height:100%}"
                + "#cc-error{position:absolute;bottom:0;inset-inline:0;padding:10px 14px;"
                + "font:14px sans-serif;background:#fbe4e1;color:#8b2319}</style></head><body>"
                + "<div id=cc-stage><iframe id=cc-fit title=\"SVG\" sandbox=\"allow-same-origin"
                + (allowScripts ? " allow-scripts" : "") + "\"></iframe></div>"
                + "<div id=cc-error hidden>无法渲染 SVG：请检查文件是否是有效的 SVG/XML。</div>"
                + "<script>" + SVG_RUNTIME + "\n"
                + "const raw=atob('" + bytes + "');const array=Uint8Array.from(raw,c=>c.charCodeAt(0));"
                + "const url=URL.createObjectURL(new Blob([array],{type:'image/svg+xml'}));"
                + "frame.src=url;</script></body></html>";
    }

    private static final String SVG_RUNTIME = """
            const stage=document.getElementById('cc-stage'), frame=document.getElementById('cc-fit');
            let svg=null, observer=null;
            function layout(){
              if(!svg) return;
              const b=svg.viewBox.baseVal, w=frame.clientWidth, h=frame.clientHeight;
              const pad=Math.min(8,w/16,h/16);
              const availW=w-2*pad, availH=h-2*pad;
              const k=b.width>0&&b.height>0?Math.min(availW/b.width,availH/b.height):0;
              const width=k?b.width*k:availW, height=k?b.height*k:availH;
              const css=svg.style;
              for(const [prop,val] of Object.entries({
                position:'absolute',display:'block',margin:'0',left:(w-width)/2+'px',
                top:(h-height)/2+'px',width:width+'px',height:height+'px',
                'min-width':'0','min-height':'0','max-width':'none','max-height':'none'
              })) css.setProperty(prop,val,'important');
            }
            frame.addEventListener('load',()=>{
              let root=null;
              try {root=frame.contentDocument.documentElement;} catch (e) {}
              svg=root&&root.namespaceURI==='http://www.w3.org/2000/svg'&&root.localName==='svg'
                 &&!root.querySelector('parsererror')?root:null;
              document.getElementById('cc-error').hidden=!!svg;
              if(!svg) return;
              layout();
              if(observer) observer.disconnect();
              observer=new MutationObserver(layout);
              observer.observe(svg,{attributes:true,attributeFilter:['viewBox']});
              svg.addEventListener('click',e=>{
                if(e.target.closest('a')) e.preventDefault();
              },true);
            });
            new ResizeObserver(layout).observe(frame);
            window.CCViewport={
              set(scale,dx,dy){stage.style.transform='translate('+dx+'px,'+dy+'px) scale('+scale+')';},
              svg(){return svg;},
              bounds(){
                if(!svg)return null;
                const r=svg.getBoundingClientRect(), f=frame.getBoundingClientRect();
                const k=frame.clientWidth?f.width/frame.clientWidth:1;
                return {x:f.left+r.left*k,y:f.top+r.top*k,w:r.width*k,h:r.height*k,
                        vw:innerWidth,vh:innerHeight};
              },
              contentBox(){
                if(!svg)return null;
                try {const {x,y,width:w,height:h}=svg.getBBox();return {x,y,w,h};} catch(e){return null;}
              }
            };
            window.CCTap=(x,y)=>{
              if(!svg)return;
              const f=frame.getBoundingClientRect(), k=f.width/frame.clientWidth;
              const cx=(x-f.left)/k, cy=(y-f.top)/k;
              const doc=svg.ownerDocument, el=doc.elementFromPoint(cx,cy);
              if(!el)return;
              const opts={bubbles:true,cancelable:true,clientX:cx,clientY:cy,button:0,pointerId:1,
                          pointerType:'touch',isPrimary:true};
              const win=doc.defaultView;
              el.dispatchEvent(new win.PointerEvent('pointerdown',opts));
              el.dispatchEvent(new win.PointerEvent('pointerup',opts));
              el.dispatchEvent(new win.MouseEvent('click',opts));
            };
            """;

    static String nativeSvg(String content, String viewBoxOverride) {
        String svg = sanitizeRootSvg(content);
        // Import has already decoded the source to a Java string. The Blob is always UTF-8.
        svg = svg.replaceFirst("(?i)(\\A\\uFEFF?<\\?xml\\b[^?]*?\\bencoding\\s*=\\s*)['\"][^'\"]*['\"]",
                "$1\"UTF-8\"");
        SvgGeometry.Box box = SvgGeometry.measure(svg);
        if (viewBoxOverride != null && viewBoxOverride.matches("[-+.eE0-9 ,]+")) {
            svg = overrideViewBox(svg, viewBoxOverride);
        } else if (box.source == SvgGeometry.Source.WIDTH_HEIGHT && box.isValid()) {
            svg = overrideViewBox(svg, "0 0 " + box.width + " " + box.height);
        }
        Matcher root = ROOT_SVG_PATTERN.matcher(svg);
        if (!root.find()) return svg;
        String attrs = root.group(1);
        String ns = attrs.matches("(?is).*\\sxmlns\\s*=.*") ? ""
                : " xmlns=\"http://www.w3.org/2000/svg\"";
        if (svg.contains("xlink:") && !attrs.matches("(?is).*\\sxmlns:xlink\\s*=.*")) {
            ns += " xmlns:xlink=\"http://www.w3.org/1999/xlink\"";
        }
        return svg.substring(0, root.start(1)) + ns + svg.substring(root.start(1));
    }

    /**
     * Rewrite the root {@code <svg>} so the drawing arrives whole:
     * <ul>
     *   <li>{@code slice} → {@code meet} (crop → contain)</li>
     *   <li>an explicit {@code preserveAspectRatio="xMidYMid meet"} added when absent</li>
     * </ul>
     */
    static String sanitizeRootSvg(String content) {
        if (content == null) return "";
        Matcher m = ROOT_SVG_PATTERN.matcher(content);
        if (!m.find()) {
            return content;
        }

        String attrs = m.group(1);
        boolean selfClosing = attrs.endsWith("/");
        String updatedAttrs = selfClosing ? attrs.substring(0, attrs.length() - 1) : attrs;

        // Replace or add preserveAspectRatio="xMidYMid meet". Sizing CSS is preview state.
        Matcher aspectMatch = PRESERVE_ASPECT_PATTERN.matcher(updatedAttrs);
        if (aspectMatch.find()) {
            String val = aspectMatch.group(1);
            if (val.toLowerCase(Locale.ROOT).contains("slice")) {
                updatedAttrs = aspectMatch.replaceFirst("preserveAspectRatio=\"xMidYMid meet\"");
            }
        } else {
            Matcher aspectAposMatch = PRESERVE_ASPECT_APOS_PATTERN.matcher(updatedAttrs);
            if (aspectAposMatch.find()) {
                String val = aspectAposMatch.group(1);
                if (val.toLowerCase(Locale.ROOT).contains("slice")) {
                    updatedAttrs = aspectAposMatch.replaceFirst("preserveAspectRatio=\"xMidYMid meet\"");
                }
            } else {
                // Not specified at all: add explicitly to guarantee 'meet'
                updatedAttrs = updatedAttrs + " preserveAspectRatio=\"xMidYMid meet\"";
            }
        }

        return content.substring(0, m.start(1)) + updatedAttrs + (selfClosing ? "/" : "")
                + content.substring(m.end(1));
    }

    /** Preview-only viewBox swap. The document's own viewBox is left in the source untouched. */
    static String overrideViewBox(String content, String viewBox) {
        if (content == null || viewBox == null) return content;
        Matcher m = ROOT_SVG_PATTERN.matcher(content);
        if (!m.find()) return content;
        String attrs = m.group(1);
        boolean selfClosing = attrs.endsWith("/");
        if (selfClosing) attrs = attrs.substring(0, attrs.length() - 1);
        String replaced;
        if (attrs.matches("(?is).*\\bviewBox\\s*=.*")) {
            replaced = attrs.replaceAll("(?is)viewBox\\s*=\\s*(\"[^\"]*\"|'[^']*')",
                    "viewBox=\"" + viewBox + "\"");
        } else {
            replaced = attrs + " viewBox=\"" + viewBox + "\"";
        }
        return content.substring(0, m.start(1)) + replaced + (selfClosing ? "/" : "")
                + content.substring(m.end(1));
    }

    /** True when the source actually contains an SVG root, so the UI can explain a failed render. */
    public static boolean hasSvgRoot(String content) {
        return content != null && ROOT_SVG_PATTERN.matcher(content).find();
    }
}
