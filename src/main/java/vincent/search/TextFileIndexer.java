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
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.search.vectorhighlight.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * package it : mvn clean package
 * run it: mvn exec:java -Dexec.mainClass="vincent.search.TextFileIndexer"
 * <p>
 * This terminal application creates an Apache Lucene index in a folder and adds files into this index
 * based on the input of the user.
 */
public class TextFileIndexer {
    private static StandardAnalyzer analyzer = new StandardAnalyzer();

    private IndexWriter writer;
    private List<File> queue = new ArrayList();


    public static void main(String[] args) throws IOException {
        System.out.println("Enter the path where the index will be created: (e.g. /tmp/index or c:\\temp\\index)");

        String indexLocation = null;
        BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in));
        String s = br.readLine();

        TextFileIndexer indexer = null;
        try {
            indexLocation = s;
            indexer = new TextFileIndexer(s);
        } catch (Exception ex) {
            System.out.println("Cannot create index..." + ex.getMessage());
            System.exit(-1);
        }

        //===================================================
        //read input from user until he enters q for quit
        //===================================================
        while (!s.equalsIgnoreCase("q")) {
            try {
                System.out.println("Enter the full path to add into the index (q=quit): (e.g. /home/ron/mydir or c:\\Users\\ron\\mydir)");
                System.out.println("[Acceptable file types: .xml, .html, .html, .txt]");
                s = br.readLine();
                if (s.equalsIgnoreCase("q")) {
                    break;
                }

                //try to add file into the index
                indexer.indexFileOrDirectoryWithChunk(s);
            } catch (Exception e) {
                System.out.println("Error indexing " + s + " : " + e.getMessage());
            }
        }

        //===================================================
        //after adding, we always have to call the
        //closeIndex, otherwise the index is not created
        //===================================================
        indexer.closeIndex();

        //=========================================================
        // Now search
        //=========================================================
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexLocation).toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);
        //TopScoreDocCollector collector = TopScoreDocCollector.create(5, 20);

        s = "";
        while (!s.equalsIgnoreCase("q")) {
            try {
                System.out.println("Enter the search query (q=quit):");
                s = br.readLine();
                if (s.equalsIgnoreCase("q")) {
                    break;
                }
                TopScoreDocCollector collector = TopScoreDocCollector.create(5, 20);

                Query q = new QueryParser("contents", analyzer).parse(s);
                searcher.search(q, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;
                System.out.println("hits" + hits);
                // 4. display results
                System.out.println("Found " + hits.length + " hits.");
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);
                    //System.out.println((i + 1) + ". " + d.get("path") + " score=" + hits[i].score + " contents=" + d.get("contents"));
//                    System.out.println((i + 1) + ". " + d.get("path")
//                            + " line=" + d.get("lineNumber")
//                            + " score=" + hits[i].score
//                            + " contents=\n" + d.get("contents"));
//
                    String text = d.get("contents");
                    TokenStream tokenStream = TokenSources.getAnyTokenStream(searcher.getIndexReader(), docId, "contents", analyzer);
                    TextFragment[] frag = new Highlighter(new SimpleHTMLFormatter(), new QueryScorer(q))
                            .getBestTextFragments(tokenStream, text, true, 10);

                    for (int j = 0; j < frag.length; j++) {
                        if ((frag[j] != null) && (frag[j].getScore() > 0)) {
                            System.out.println((i + 1) + ". " + d.get("path") + " score=" + hits[i].score);
                            System.out.println((frag[j].toString()));
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("Error searching " + s + " : " + e.getMessage());
            }
        }

    }

    /**
     * Constructor
     *
     * @param indexDir the name of the folder in which the index should be created
     * @throws java.io.IOException when exception creating index.
     */
    TextFileIndexer(String indexDir) throws IOException {
        // the boolean true parameter means to create a new index everytime,
        // potentially overwriting any existing files there.
        FSDirectory dir = FSDirectory.open(new File(indexDir).toPath());


        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        writer = new IndexWriter(dir, config);
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
	
	
	/*public List<String> readLinesAround(BufferedReader reader, String targetLine) throws IOException {
		LinkedList<String> buffer = new LinkedList<>();
		String line;
		while ((line = reader.readLine()) != null) {
			buffer.add(line);
			if (buffer.size() > 7) { // Adjust this value to control how many lines around the target you want to capture
				buffer.pop();
			}
			if (buffer.size() == 4 && line.equals(targetLine)) { // found target line, it is now in the middle of the buffer
				for (int i = 0; i < 3; i++) { // read the next 3 lines
					line = reader.readLine();
					if (line != null) {
						buffer.add(line);
					} else {
						break;
					}
				}
				return new ArrayList<>(buffer); // return copy of buffer as a List
			}
		}
		return null; // target line not found in the file
	}*/

}