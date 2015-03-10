import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String sqlCommonPart = "select moz_bookmarks.id, parent, position, moz_bookmarks.type, " +
            "moz_bookmarks.title, moz_places.url, moz_items_annos.content as description\n" +
            "from moz_bookmarks\n" +
            "left outer join moz_places on fk = moz_places.id\n" +
            "left outer join moz_items_annos on moz_bookmarks.id = moz_items_annos.item_id\n" +
            "where (moz_items_annos.type is null or moz_items_annos.type = 3) and moz_bookmarks.type <= 2\n";

    private static final String sqlNaturalOrder = sqlCommonPart + "order by parent, position;";

    //Difference is only in ordering
    public static final String sqlLinksFirst = sqlCommonPart + "order by parent, moz_bookmarks.type, position;";

    private static String wikiDir;
    private static String sqliteFile;

    //True = link text = bookmark title, false = bookmark title is a separate text in front of a link.
    private static boolean compactBookmarks;

    /**
     * The maximal number of elements (recursive size) of a page. If larger than that, child directories will be
     * created on separate subpages.
     */
    private static int pageSizeThreshold;

    private static int pagesCreated = 0;
    private static boolean previousNewLine = true;

    public static void main(String args[]) throws IOException {
        parseArgs(args);
        Connection connection;
        Statement stmt;
        Node root = null;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
            System.out.println("Opened database successfully");

            stmt = connection.createStatement();
            String sql = pageSizeThreshold <= 1 ? sqlNaturalOrder : sqlLinksFirst;
            ResultSet rs = stmt.executeQuery(sql);
            root = readNodes(rs);
            rs.close();
            stmt.close();
            connection.close();
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
        printWiki(root);
        printTree(root);
    }

    /**
     * @param args -in: path to place.sql
     *             <p>
     *             -out: path to wiki root directory, the one containing directory "data"
     *             <p>
     *             -compatBookmarks: true = bookmarks a la [name|link],
     *             false = boolmarks a la name: link
     *             <p>
     *             -pageSizeThreshold: The maximal number of elements (links and directories) a page may contain.
     *             if a page has less than pageSizeThreshold elements, all its elements will be
     *             recursively included into this page. Set to 1 to have one page for each directory.
     *             Set to a large value, like 10000, to generate the whole wiki in one page.
     */
    private static void parseArgs(String[] args) {
        if (args.length != 8) {
            System.err.println("Error! Expected 8 arguments, got " + args.length + ". Command-line arguments:\n");
            for (String arg : args) {
                System.err.println(arg);
            }
            System.exit(1);
        }
        try {
            sqliteFile = args[1];
            wikiDir = args[3];
            compactBookmarks = Boolean.parseBoolean(args[5]);
            pageSizeThreshold = Integer.parseInt(args[7]);
        } catch (RuntimeException e) {
            System.err.println("An exception occurred. Command-line arguments:\n");
            for (String arg : args) {
                System.err.println(arg);
            }
            System.err.println();
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * @return The root node
     */
    private static Node readNodes(ResultSet rs) throws SQLException {
        List<Node> nodes = new ArrayList<>();
        while (rs.next()) {
            int id = rs.getInt("id");
            int parent = rs.getInt("parent");
            int position = rs.getInt("position");
            int type = rs.getInt("type");
            String title = rs.getString("title");
            String url = rs.getString("url");
            String description = rs.getString("description");

            nodes.add(new Node(id, parent, position, type, title, url, description));
        }

        Map<Integer, Node> nodesMap = new HashMap<>();
        for (Node node : nodes) {
            nodesMap.put(node.id, node);
        }

        for (Node node : nodes) {
            node.parent = nodesMap.get(node.parentId);
            if (node.parent != null) {
                node.parent.children.add(node);
            }
        }

        if (nodes.get(0).parent == null) {
            return nodes.get(0);
        } else {
            throw new RuntimeException("Invalid bookmarks tree");
        }
    }

    private static void printTree(Node root) {
        //System.out.println("\nBookmarks tree:\n" + root);
        System.out.println("\nTotal nodes: " + root.sizeRecursively());
        System.out.println("Pages created: " + pagesCreated + "\n");
    }

    private static void printWiki(Node root) throws IOException {
        printNode(root, null, 0, false);
    }

    private static void printNode(Node node, Writer writer, int level, boolean samePage) throws IOException {
        if (node.isDirectory) {
            writeDir(node, writer, level, samePage);
        } else {
            writeLink(node, writer);
        }
    }

    private static void writeLink(Node node, Writer writer) throws IOException {
        if (compactBookmarks) {
            writer.write("    * [");
            if (node.url != null) {
                writer.write(node.url);
            }
            writer.write(" | ");
            if (node.title != null) {
                writer.write(node.escapedTitle());
            }
            writer.write("]");
        } else { //non-compact bookmarks
            writer.write("    * ");
            if (node.title != null) {
                writer.write(node.escapedTitle());
            }
            writer.write(": ");
            if (node.url != null) {
                writer.write(node.url);
            }
        }

        if (node.description != null) {
            writer.write(" ");
            writer.write(node.escapedDescription());
        }
        writer.write("\n");
        previousNewLine = false;
    }

    /**
     * @param node     the current node.
     * @param writer   the writer for the current file. Null only for root node.
     * @param level    the heading level for this dir, if same page. 0 for separate pages.
     * @param samePage true if this dir should stay on the same page with its parent, false if new page should be
     *                 created.
     * @throws IOException
     */
    private static void writeDir(Node node, Writer writer, int level, boolean samePage) throws IOException {
        if (!node.children.isEmpty()) {
            if (writer != null && samePage) {
                writeDirOnSamePage(node, writer, level);
            } else {
                writeDirOnNewPage(node, writer);
            }
        }
    }

    private static void writeDirOnSamePage(Node node, Writer writer, int level) throws IOException {
        if (!previousNewLine) {
            writer.write("\n");
        }
        for (int i = 0; i < level; i++) {
            writer.write("+");
        }
        writer.write(" ");
        writer.write(node.escapedTitle());
        writer.write("\n\n");
        previousNewLine = true;
        for (Node child : node.children) {
            printNode(child, writer, level + 1, true);
        }
    }

    private static void writeDirOnNewPage(Node node, Writer writer) throws IOException {
        String title = writer != null ? node.titleAsFile() : getRootTitle();
        String pageFileName = title + ".wiki";
        File pageFile = new File(wikiDir + "\\data\\" + pageFileName);

        //Root page may already exist, it will be overwritten.
        if (writer != null && pageFile.exists()) {
            System.out.println("Page already exists:        " + title
                    + "\n    parent:                                       " + node.getParentTitle());
            title = title + " - " + node.getParentTitle();
            pageFileName = title + ".wiki";
            pageFile = new File(wikiDir + "\\data\\" + pageFileName);
            System.out.println("Creating alternative page:  " + title + "\n");
        }
        if (writer == null || !pageFile.exists()) {
            //may be null only for root node
            if (writer != null) {
                writer.write("    * [" + title + "]\n");
            }
            try (Writer newPageWriter = new FileWriter(pageFile)) {
                boolean childrenOnSamePage = node.sizeRecursively() <= pageSizeThreshold;
                newPageWriter.write("++ " + title + "\n\n");
                if (childrenOnSamePage && node.containsDirs()) {
                    newPageWriter.write("[:toc:]\n\n");
                }
                previousNewLine = true;
                for (Node child : node.children) {
                    printNode(child, newPageWriter, childrenOnSamePage ? 3 : 0, childrenOnSamePage);
                }
                if (writer == null) {
                    newPageWriter.write("\n\t* WikiSettings\n");
                }
                pagesCreated++;
            }
        } else {
            System.out.println("ERROR!!!   Alternative page also exists. Skipping.");
        }
    }

    public static String getRootTitle() {
        return new File(wikiDir).getName();
    }
}
