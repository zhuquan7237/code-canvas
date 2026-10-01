package com.nous.codecanvas.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewKeyTest {
 @Test public void sameArtworkReusesKey(){ assertEquals(PreviewKey.forDocument("a","<svg/>",false),PreviewKey.forDocument("a","<svg/>",false)); }
 @Test public void editInvalidatesScreenshot(){ assertNotEquals(PreviewKey.forDocument("a","one",false),PreviewKey.forDocument("a","two",false)); }
 @Test public void themeAndDocumentInvalidateScreenshot(){ assertNotEquals(PreviewKey.forDocument("a","one",false),PreviewKey.forDocument("a","one",true)); assertNotEquals(PreviewKey.forDocument("a","one",false),PreviewKey.forDocument("b","one",false)); }
 @Test public void keyCannotEscapeCacheDirectory(){ assertTrue(PreviewKey.forDocument("../../秘密","<svg/>",false).matches("[0-9a-f]{64}")); }
}
