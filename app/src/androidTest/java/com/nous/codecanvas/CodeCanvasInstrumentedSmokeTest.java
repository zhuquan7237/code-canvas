package com.nous.codecanvas;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityUnitTestCase;
import android.test.InstrumentationTestCase;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;
import com.nous.codecanvas.util.XmlValidator;

import java.io.File;
import java.util.List;

public class CodeCanvasInstrumentedSmokeTest extends InstrumentationTestCase {

    private Context targetContext;
    private DocumentRepository repository;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(targetContext);
    }

    public void testRepositoryDurabilityAndSeeding() throws Exception {
        assertNotNull("Target context should not be null", targetContext);
        repository.seedDefaultTemplatesIfEmpty();
        List<CanvasDocument> docs = repository.getAllDocuments();
        assertTrue("Repository should have seeded documents", docs.size() >= 3);

        // Test creating and persisting custom file
        String testId = "smoke-test-" + System.currentTimeMillis();
        CanvasDocument newDoc = new CanvasDocument(testId, "instrumented.svg", "<svg><circle r=\"10\"/></svg>", System.currentTimeMillis());
        repository.saveDocument(newDoc);

        CanvasDocument fetched = repository.findById(testId);
        assertNotNull("Document must be retrievable", fetched);
        assertEquals("instrumented.svg", fetched.getTitle());
        assertEquals(RenderKind.SVG, RenderKindDetector.detect(fetched.getTitle(), fetched.getContent()));

        // Cleanup
        repository.deleteDocument(testId);
        assertNull(repository.findById(testId));
    }

    public void testXmlSecurityEngineUnderAndroidRuntime() {
        // Valid XML check
        XmlValidator.ValidationResult result = XmlValidator.validateSecurely("<config><key>test</key></config>");
        assertTrue("Standard valid XML should pass", result.isValid());

        // Malformed check
        XmlValidator.ValidationResult malformed = XmlValidator.validateSecurely("<unclosed>");
        assertFalse("Malformed XML must fail", malformed.isValid());

        // XXE payload check
        String xxe = "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///proc/version\"> ]><foo>&xxe;</foo>";
        XmlValidator.ValidationResult xxeResult = XmlValidator.validateSecurely(xxe);
        assertFalse("XXE payload must be securely intercepted", xxeResult.isValid());
    }
}
