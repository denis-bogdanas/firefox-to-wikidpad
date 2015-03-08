import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Bogdanas
 *         Created on 07/03/15.
 */
public class Node {
    int id;
    int parentId;
    int position;
    boolean isDirectory;
    String title;
    String url;
    String description;
    Node parent;
    List<Node> children = new ArrayList<>();

    public Node(int id, int parentId, int position, int type, String title, String url, String description) {
        this.id = id;
        this.parentId = parentId;
        this.position = position;
        this.isDirectory = type == 2;
        this.title = title;
        this.url = url;
        this.description = description;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public int sizeRecursively() {
        return children.stream().mapToInt(Node::sizeRecursively).sum() + 1;
    }

    public boolean containsDirs() {
        return children.stream().map(Node::isDirectory).anyMatch(p -> true);
    }

    public String escapedTitle() {
        return escape(title);
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]").replace("|", "\\|");
    }

    public String escapedDescription() {
        return escape(description);
    }

    @Override
    public String toString() {
        return toString("").toString();
    }

    public CharSequence toString(String indent) {
        return new StringBuilder()
                .append("Node{")
                .append("id=").append(id)
                .append(", position=").append(position)
                .append(", isDirectory=").append(isDirectory)
                .append(", title='").append(title).append('\'')
                .append(", url='").append(url).append('\'')
                .append(", description='").append(description).append('\'')
                .append(", size=").append(sizeRecursively())
                .append(printChildren(children, indent))
                .append('}');
    }

    private CharSequence printChildren(List<Node> children, String indent) {
        if (children.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String childIndent = indent + "  ";
        result.append(", children[\n");
        for (Node child : children) {
            result.append(childIndent).append(child.toString(childIndent)).append(",\n");
        }
        result.append(indent).append("]");
        return result;
    }

    public String titleAsFile() {
        return encodeFileName(title);
    }

    public String getParentTitle() {
        return parent != null ? parent.titleAsFile() : "";
    }

    //Source: http://stackoverflow.com/questions/1184176/how-can-i-safely-encode-a-string-in-java-to-use-as-a-filename
    private String encodeFileName(String s) {
        String pattern = "[^a-zA-Z0-9_\\-\\.% ]";
        int len = s.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            String chs = String.valueOf(ch);
            if (chs.matches(pattern)) {
                //simply delete the char;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
