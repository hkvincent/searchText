package vincent.search;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class TextFileIndexerUI {

    private TextFileIndexerForUI indexer;
    private static final String PROPERTIES_FILE_PATH = "config.properties";
    private static final String INDEX_DIR_PROPERTY = "indexDir";

    private Properties properties;
    private String indexDir;


    public TextFileIndexerUI() {
        // Load properties
        properties = new Properties();
        loadProperties();


        // Create and set up the window.
        JFrame frame = new JFrame("TextFileIndexer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 800);
        frame.setLayout(new BorderLayout());
        JTextArea textArea = new JTextArea(30, 20);
        textArea.setEditable(false);  // make textArea non-editable
        JScrollPane scrollPane = new JScrollPane(textArea);
        // Create main panel with vertical BoxLayout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Add index creation panel
        JPanel indexPanel = new JPanel();
        JTextField indexField = new JTextField(indexDir, 30);
        JButton indexButton = new JButton("Create Index");
        indexButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    indexer = new TextFileIndexerForUI(indexField.getText());
                    indexDir = indexField.getText();
                    saveProperties();
                    JOptionPane.showMessageDialog(frame, "Index created successfully");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error creating index: " + ex.getMessage());
                }
            }
        });
        indexPanel.add(new JLabel("Index Location:"));
        indexPanel.add(indexField);
        indexPanel.add(indexButton);
        mainPanel.add(indexPanel);

        // Add file indexing panel
        JPanel filePanel = new JPanel();
        JTextField fileField = new JTextField(30);
        JButton fileButton = new JButton("Index File");
        fileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    indexer.indexFileOrDirectoryWithChunk(fileField.getText());
                    JOptionPane.showMessageDialog(frame, "File indexed successfully");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error indexing file: " + ex.getMessage());
                }
            }
        });
        filePanel.add(new JLabel("File to index:"));
        filePanel.add(fileField);
        filePanel.add(fileButton);
        mainPanel.add(filePanel);

        // Add search panel
        JPanel searchPanel = new JPanel();
        JTextField searchField = new JTextField(30);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // perform search and show results in a dialog
                // replace this with your actual search implementation
                String query = searchField.getText();
                String searchResults = "null"; // Replace this with actual search results
                try {
                    searchResults = indexer.search(query);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                textArea.setText(searchResults);
            }
        });
        searchPanel.add(new JLabel("Search query:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);

        // Center the JFrame on screen
        frame.setLocationRelativeTo(null);
        mainPanel.add(searchPanel);
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);

        // Auto-create the index if the indexDir property is set
        if (indexDir != null && !indexDir.isEmpty()) {
            try {
                indexer = new TextFileIndexerForUI(indexDir);
                JOptionPane.showMessageDialog(frame, "Index created successfully");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error creating index: " + ex.getMessage());
            }
        }
    }

    private void loadProperties() {
        try (FileInputStream in = new FileInputStream(PROPERTIES_FILE_PATH)) {
            properties.load(in);
            indexDir = properties.getProperty(INDEX_DIR_PROPERTY, "");
        } catch (IOException e) {
            // Handle exception
        }
    }

    private void saveProperties() {
        properties.setProperty(INDEX_DIR_PROPERTY, indexDir);
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE_PATH)) {
            properties.store(out, null);
        } catch (IOException e) {
            // Handle exception
        }
    }

    public static void main(String[] args) {
        new TextFileIndexerUI();
    }
}
