package vincent.search;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TextFileIndexerForUI {
    private static StandardAnalyzer analyzer = new StandardAnalyzer();

    public IndexWriter writer;
    public List<File> queue = new ArrayList();
    public TextFileIndexerForUI indexer;
    public String indexLocation;

    /**
     * Constructor
     *
     * @param indexDir the name of the folder in which the index should be created
     * @throws java.io.IOException when exception creating index.
     */
    TextFileIndexerForUI(String indexDir) throws IOException {
        // the boolean true parameter means to create a new index everytime,
        // potentially overwriting any existing files there.
        FSDirectory dir = FSDirectory.open(new File(indexDir).toPath());


        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        writer = new IndexWriter(dir, config);

        indexLocation = indexDir;
    }


    public String search(String search) throws IOException {

        //=========================================================
        // Now search
        //=========================================================
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexLocation).toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);
        //TopScoreDocCollector collector = TopScoreDocCollector.create(5, 20);

        StringBuilder sb = new StringBuilder();
        try {
            TopScoreDocCollector collector = TopScoreDocCollector.create(5, 20);

            Query q = new QueryParser("contents", analyzer).parse(search);
            searcher.search(q, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;
            System.out.println("hits" + hits);
            // 4. display results
            System.out.println("Found " + hits.length + " hits.");
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);

//
//                System.out.println((i + 1) + ". " + d.get("path") + " score=" + hits[i].score + " contents=" + d.get("contents"));
//                System.out.println((i + 1) + ". " + d.get("path")
//                        + " line=" + d.get("lineNumber")
//                        + " score=" + hits[i].score
//                        + " contents=\n" + d.get("contents"));

                String text = d.get("contents");
                TokenStream tokenStream = TokenSources.getAnyTokenStream(searcher.getIndexReader(), docId, "contents", analyzer);
                TextFragment[] frag = new Highlighter(new SimpleHTMLFormatter(), new QueryScorer(q))
                        .getBestTextFragments(tokenStream, text, true, 10);

                for (int j = 0; j < frag.length; j++) {
                    if ((frag[j] != null) && (frag[j].getScore() > 0)) {
                        sb.append((i + 1) + ". " + d.get("path") + " score=" + hits[i].score);
                        sb.append(frag[j].toString());
                        sb.append(System.lineSeparator());
                        System.out.println((i + 1) + ". " + d.get("path") + " score=" + hits[i].score);
                        System.out.println((frag[j].toString()));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error searching " + search + " : " + e.getMessage());
        }
        return sb.toString();
    }


    /**
     * Indexes a file or directory
     *
     * @param fileName the name of a text file or a folder we wish to add to the index
     * @throws java.io.IOException when exception
     */
    public void indexFileOrDirectoryWithChunk(String fileName) throws IOException {
        //===================================================
        //gets the list of files in a folder (if user has submitted
        //the name of a folder) or gets a single file name (is user
        //has submitted only the file name)
        //===================================================
        addFiles(new File(fileName));

        int originalNumDocs = writer.getDocStats().numDocs;
        for (File f : queue) {
            //FileReader fr = null;
            try {
                //Document doc = new Document();

                //===================================================
                // add contents of file
                //===================================================

                // read the file line by line
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line;
                    StringBuilder paragraph = new StringBuilder();
                    int lineNumber = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        paragraph.append(line).append(" ");

                        // index every 5 lines as a separate chunk
                        if (lineNumber % 5 == 0) {
                            Document doc = new Document();
                            doc.add(new TextField("contents", paragraph.toString(), Field.Store.YES));
                            doc.add(new StringField("path", f.getPath(), Field.Store.YES));
                            doc.add(new StringField("filename", f.getName(), Field.Store.YES));
                            doc.add(new StringField("lineNumber", Integer.toString(lineNumber), Field.Store.YES));
                            writer.addDocument(doc);

                            // reset paragraph
                            paragraph.setLength(0);
                        }
                    }

                    // don't forget the last paragraph if the total number of lines is not a multiple of 5
                    if (paragraph.length() > 0) {
                        Document doc = new Document();
                        doc.add(new TextField("contents", paragraph.toString(), Field.Store.YES));
                        doc.add(new StringField("path", f.getPath(), Field.Store.YES));
                        doc.add(new StringField("filename", f.getName(), Field.Store.YES));
                        doc.add(new StringField("lineNumber", Integer.toString(lineNumber), Field.Store.YES));
                        writer.addDocument(doc);
                    }
                }

				/*
				fr = new FileReader(f);

                doc.add(new TextField("contents", fr));
                doc.add(new StringField("path", f.getPath(), Field.Store.YES));
                doc.add(new StringField("filename", f.getName(), Field.Store.YES));

                writer.addDocument(doc);*/
                System.out.println("Added: " + f);
            } catch (Exception e) {
                System.out.println("Could not add: " + f);
            }
        }

        int newNumDocs = writer.getDocStats().numDocs;
        System.out.println("");
        System.out.println("************************");
        System.out.println((newNumDocs - originalNumDocs) + " documents added.");
        System.out.println("************************");

        this.closeIndex();

        queue.clear();
    }

    public void indexFileOrDirectory(String fileName) throws IOException {
        //===================================================
        //gets the list of files in a folder (if user has submitted
        //the name of a folder) or gets a single file name (is user
        //has submitted only the file name)
        //===================================================
        addFiles(new File(fileName));

        int originalNumDocs = writer.getDocStats().numDocs;
        for (File f : queue) {
            FileReader fr = null;
            try {
                Document doc = new Document();

                //===================================================
                // add contents of file
                //===================================================
                fr = new FileReader(f);
                doc.add(new TextField("contents", readFileString(f.getPath()), Field.Store.YES));
                doc.add(new StringField("path", f.getPath(), Field.Store.YES));
                doc.add(new StringField("filename", f.getName(), Field.Store.YES));

                writer.addDocument(doc);
                System.out.println("Added: " + f);
            } catch (Exception e) {
                System.out.println("Could not add: " + f);
            } finally {
                fr.close();
            }
        }

        int newNumDocs = writer.getDocStats().numDocs;
        System.out.println("");
        System.out.println("************************");
        System.out.println((newNumDocs - originalNumDocs) + " documents added.");
        System.out.println("************************");

        queue.clear();

        this.closeIndex();
    }

    public static String readFileString(String file) {
        StringBuffer text = new StringBuffer();
        try {

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(file)), "UTF8"));
            String line;
            while ((line = in.readLine()) != null) {
                text.append(line + "\r\n");
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return text.toString();
    }


    private void addFiles(File file) {

        if (!file.exists()) {
            System.out.println(file + " does not exist.");
        }
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                addFiles(f);
            }
        } else {
            String filename = file.getName().toLowerCase();
            //===================================================
            // Only index text files
            //===================================================
            if (filename.endsWith(".htm") || filename.endsWith(".html") ||
                    filename.endsWith(".xml") || filename.endsWith(".txt")) {
                queue.add(file);
            } else {
                System.out.println("Skipped " + filename);
            }
        }
    }

    /**
     * Close the index.
     *
     * @throws java.io.IOException when exception closing
     */
    public void closeIndex() throws IOException {
        writer.close();
    }

}
