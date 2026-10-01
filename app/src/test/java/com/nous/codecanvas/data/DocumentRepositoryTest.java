package com.nous.codecanvas.data;

import com.nous.codecanvas.model.CanvasDocument;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;

public class DocumentRepositoryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private DocumentRepository repository;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = folder.newFolder("canvas_data");
        repository = new DocumentRepository(tempDir);
    }

    @Test
    public void testSeedDefaultTemplates() throws Exception {
        assertTrue("Initially empty", repository.getAllDocuments().isEmpty());
        repository.seedDefaultTemplatesIfEmpty();
        List<CanvasDocument> docs = repository.getAllDocuments();
        assertEquals(3, docs.size());
    }

    @Test
    public void testSaveAndReloadDurabilityWithUnicode() throws Exception {
        String unicodeTitle = "我的测试画布 🎨 🚀.custom-ext";
        String unicodeContent = "<html>\n<body>\n<h1>你好，世界！</h1>\n<p>Emoji 测试: 💡 ⚡ 📐 🦄</p>\n</body>\n</html>";
        
        CanvasDocument doc = new CanvasDocument("test-uuid-1", unicodeTitle, unicodeContent, System.currentTimeMillis());
        repository.saveDocument(doc);

        // Verify finding by ID
        CanvasDocument loaded = repository.findById("test-uuid-1");
        assertNotNull(loaded);
        assertEquals(unicodeTitle, loaded.getTitle());
        assertEquals(unicodeContent, loaded.getContent());

        // Create new repository instance pointing to same file to verify cold persistence
        DocumentRepository repo2 = new DocumentRepository(tempDir);
        CanvasDocument loadedFromCold = repo2.findById("test-uuid-1");
        assertNotNull(loadedFromCold);
        assertEquals(unicodeTitle, loadedFromCold.getTitle());
        assertEquals(unicodeContent, loadedFromCold.getContent());
    }

    @Test
    public void testDeleteDocument() throws Exception {
        CanvasDocument doc1 = new CanvasDocument("1", "doc1.html", "c1", System.currentTimeMillis());
        CanvasDocument doc2 = new CanvasDocument("2", "doc2.svg", "c2", System.currentTimeMillis());
        repository.saveDocument(doc1);
        repository.saveDocument(doc2);
        assertEquals(2, repository.getAllDocuments().size());

        repository.deleteDocument("1");
        assertEquals(1, repository.getAllDocuments().size());
        assertNull(repository.findById("1"));
        assertNotNull(repository.findById("2"));
    }

    @Test
    public void testSeedOnlyOnceEverAcrossDeleteAll() throws Exception {
        // First seeding creates default templates
        repository.seedDefaultTemplatesIfEmpty();
        List<CanvasDocument> initialDocs = repository.getAllDocuments();
        assertEquals(3, initialDocs.size());

        // User deletes all documents
        for (CanvasDocument doc : initialDocs) {
            repository.deleteDocument(doc.getId());
        }
        assertTrue("All documents deleted by user", repository.getAllDocuments().isEmpty());

        // Subsequent call to seedDefaultTemplatesIfEmpty MUST NOT re-seed deleted templates
        repository.seedDefaultTemplatesIfEmpty();
        assertTrue("Should remain empty after user deliberately cleared all documents", repository.getAllDocuments().isEmpty());

        // Even across a fresh repository instance pointing to same directory
        DocumentRepository repo2 = new DocumentRepository(tempDir);
        repo2.seedDefaultTemplatesIfEmpty();
        assertTrue("Should still remain empty on fresh instance", repo2.getAllDocuments().isEmpty());
    }

    @Test
    public void testConcurrentTwoInstancesWritesNoLostDocuments() throws Exception {
        DocumentRepository repo1 = new DocumentRepository(tempDir);
        DocumentRepository repo2 = new DocumentRepository(tempDir);

        int countPerRepo = 25;
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < countPerRepo; i++) {
                try {
                    repo1.saveDocument(new CanvasDocument("repo1-doc-" + i, "doc1_" + i + ".html", "<div id='1_" + i + "'>Content</div>", System.currentTimeMillis()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < countPerRepo; i++) {
                try {
                    repo2.saveDocument(new CanvasDocument("repo2-doc-" + i, "doc2_" + i + ".svg", "<svg id='2_" + i + "'></svg>", System.currentTimeMillis()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        DocumentRepository verifier = new DocumentRepository(tempDir);
        List<CanvasDocument> all = verifier.getAllDocuments();
        assertEquals("All documents from concurrent instances must be preserved without loss", countPerRepo * 2, all.size());
    }

}
