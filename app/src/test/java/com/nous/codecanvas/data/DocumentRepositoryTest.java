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
}
