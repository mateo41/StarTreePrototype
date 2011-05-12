/*
 * @(#)STMultipleView.java
 *
 * Copyright (c) 2001-2005 Inxight Software, Inc.  All rights reserved.
 */

import com.inxight.st.*;
import com.inxight.st.io.stc.STCReader;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Enumeration;

/**
 * The STMultipleView is a demonstration application that will display a default 
 * or command line specified STC data file as a StarTree in multiple windows.
 * A Swing JSplitPane is used to show the same tree data in two windows.
 * <p>
 * Both of the Windows can be StarTree's, or one can be a JTree to demonstrate
 * the StarTree's compatibility with javax.swing.JTree because StarTree uses
 * the same underlying data model.
 * <p>
 * A StarTree window of the subtrees of any node can be opened with the popup 
 * menu.  This can be done either within the existing window, or in a new
 * window, demonstrating another way multiple views can be created.
 */
public class STMultipleView extends JFrame implements PropertyChangeListener {

    private JMenuItem miSplitVert, miSplitHoriz, miSplitJTree, miUnsplit;
    private Container main_pane;
    private Component st_pane;
    private STPanel2 stPanel1, stPanel2;
    private JTree jTree;
    private StarTree star1, star2;
    private TreeDataModel tree;
    private JTextField msg_text_field;
    TreeDragSource ds;

    TreeDropTarget dt;

    private static int window_count = 0;
    private static WindowListener window_listener = new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
            Window window = (Window) e.getSource();
            window.setVisible(false);
            window_count--;
            if (window_count <= 0)
                System.exit(0);
        }
    };

    /** 
     * Constructs a StarTree Multiple View demonstration of the specified
     * tree.
     */
    public STMultipleView(TreeDataModel tree) {
        super("StarTree(R) Multiple View Demo");

        main_pane = getContentPane();
        main_pane.setLayout(new BorderLayout());
        setSize(400, 400);

        st_pane = stPanel1 = new STMultipleViewPanel();
        main_pane.add("Center", st_pane);
        star1 = stPanel1.getStarTree();
        stPanel1.addPropertyChangeListener(this);

        msg_text_field = new JTextField();
        msg_text_field.setEditable(false);
        msg_text_field.setBorder(null);
        msg_text_field.setColumns(50);
        main_pane.add("South", msg_text_field);

        JMenuBar menu_bar = new JMenuBar();
        JMenuItem item;

        JMenu file_menu = new JMenu("File");
        file_menu.setMnemonic('F');
        file_menu.add(item = new JMenuItem("Exit", 'X'));
        item.addActionListener(new MethodCallAction(this, "exit"));
        menu_bar.add(file_menu);

        JMenu edit_menu = new JMenu("Edit");
        edit_menu.setMnemonic('E');
        edit_menu.add(item = new JMenuItem("Reset Tree", 'R'));
        item.addActionListener(new MethodCallAction(this, "reset"));
        menu_bar.add(edit_menu);

        JMenu view_menu = new JMenu("View");
        view_menu.setMnemonic('V');
        view_menu.add(miSplitHoriz = new JMenuItem("Split Horizontally", 'H'));
        miSplitHoriz.addActionListener(new MethodCallAction(this, "splitHoriz"));
        view_menu.add(miSplitVert = new JMenuItem("Split Vertically", 'V'));
        miSplitVert.addActionListener(new MethodCallAction(this, "splitVert"));
        view_menu.add(miSplitJTree = new JMenuItem("Split JTree", 'K'));
        miSplitJTree.addActionListener(new MethodCallAction(this, "splitJTree"));
        view_menu.add(miUnsplit = new JMenuItem("Unsplit", 'U'));
        miUnsplit.addActionListener(new MethodCallAction(this, "unsplit"));
        menu_bar.add(view_menu);

        setJMenuBar(menu_bar);
        setupMenus();

        // Install the license
        addLicense(star1);

        if (tree == null)
            tree = new StdGraphDataModel();
        this.tree = tree;
        star1.setTree(tree);

        window_count++;
        addWindowListener(window_listener);
    }

    /** A MethodCallAction invoked from the menu. */
    public void exit() {
        setVisible(false);
        System.exit(0);
    }
    
    /** A MethodCallAction invoked from the menu. */
    public void reset() {
        star1.setTree(tree);
        stPanel1.repaintAll();

        if (star2 != null) {
            star2.setTree(tree);
            stPanel2.repaintAll();
        }
    }
 
    /** A MethodCallAction invoked from the menu. */
    public void unsplit() {
        main_pane.remove(st_pane);
        st_pane = stPanel1;
        main_pane.add(st_pane);
        main_pane.validate();
        setupMenus();
    }

    /** A MethodCallAction invoked from the menu. */
    public void splitHoriz() {
        split(JSplitPane.HORIZONTAL_SPLIT);
    }

    /** A MethodCallAction invoked from the menu. */
    public void splitVert() {
        split(JSplitPane.VERTICAL_SPLIT);
    }
    
    /** A MethodCallAction invoked from the menu. */
    public void splitJTree() {
        main_pane.remove(st_pane);
        if (stPanel2 != null)
            stPanel2.removePropertyChangeListener(this);
        jTree = new JTree(tree);
        ds = new TreeDragSource(jTree, DnDConstants.ACTION_MOVE);

        dt = new TreeDropTarget(jTree);
       
        jTree.addPropertyChangeListener(this);

        JSplitPane split_pane = 
            new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stPanel1, new JScrollPane(jTree));

        split_pane.setDividerSize(4);
        st_pane = split_pane;
        main_pane.add(st_pane);
        main_pane.validate();
        split_pane.setDividerLocation(0.5);
        repaint();
          
        setupMenus();
    }

    /** Splits or reorients the pane. */
    private void split(int orientation) {
        if (st_pane instanceof JSplitPane) {
            ((JSplitPane) st_pane).setOrientation(orientation);
            ((JSplitPane) st_pane).setDividerLocation(0.5);
        } else {
            main_pane.remove(st_pane);
            if (stPanel2 != null)
                stPanel2.removePropertyChangeListener(this);
            stPanel2 = new STMultipleViewPanel();
            stPanel2.addPropertyChangeListener(this);
            star2 = stPanel2.getStarTree();
            addLicense(star2);
            star2.setTree(star1.getTree());

            JSplitPane split_pane = 
                new JSplitPane(orientation, stPanel1, stPanel2);

            split_pane.setDividerSize(4);
            st_pane = split_pane;
            main_pane.add(st_pane);
            main_pane.validate();
            split_pane.setDividerLocation(0.5);
            repaint();
        }
        setupMenus();
    }

    /** The implementation of the PropertyChangeListener. */
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals("message"))
            setMessage((String) e.getNewValue());
    }
    
    public void setMessage(String msg) {
        msg_text_field.setText(msg);
    }

    private void setupMenus() {
        if (st_pane instanceof JSplitPane) {
            if (((JSplitPane) st_pane).getOrientation() 
                                == JSplitPane.HORIZONTAL_SPLIT) {
                miSplitHoriz.setEnabled(false);
                miSplitVert.setEnabled(true);
            } else {
                miSplitHoriz.setEnabled(true);
                miSplitVert.setEnabled(false);
            }
            miUnsplit.setEnabled(true);
        } else {
              miSplitHoriz.setEnabled(true);
              miSplitVert.setEnabled(true);
              miUnsplit.setEnabled(false);
        }
    }

    private void addLicense(StarTree star) {
        String base_dir = System.getProperty("user.dir");
        try {
        	
            star.setLicense(null,
                    base_dir + "/res/license/license.dat",
                    base_dir + "/res/license/company.dat");
        } catch (STLicenseException e) {
            setMessage("Invalid license.");
        }
    }


   

    /**
     * A subclass of STPanel2 that includes a popupmenu for viewing 
     * subtrees.
     */
    private static class STMultipleViewPanel extends STPanel2 {

        private StarTree star;

        STMultipleViewPanel() {
            super();
            STMultipleViewPanel.this.star = getStarTree();
            // Display duplicates for graph data.
            ((StdPainter)star.getPainter()).setStyle(
                StdPainter.STYLE_DUPLICATION, true);
            JPopupMenu popup = new JPopupMenu();
            JMenuItem item;
            popup.add(item = new JMenuItem("View Subtree"));
            item.addActionListener(
                new MethodCallAction(STMultipleViewPanel.this, "viewSubtree"));
            popup.add(item = new JMenuItem("View Subtree in New Window"));
            item.addActionListener(new MethodCallAction(
                    STMultipleViewPanel.this, "viewNewSubtree"));
            setNodePopup(popup);
            
 
            
        }

        public void viewSubtree(boolean useNewWindow) {
            Link link = getPopupNode();
            if (link != null) {
                Object node = link.getLinkChild();
                TreeDataModel new_tree;
                if (node instanceof GraphDataNode)
                    new_tree = new StdGraphDataModel((GraphDataNode) node);
                else
                    new_tree = new StdTreeDataModel((TreeDataNode) node);
                if (useNewWindow) {
                    JFrame new_frame = new STMultipleView(new_tree);
                    new_frame.setVisible(true);
                } else {
                    star.setTree(new_tree);
                    repaintAll();
                }
            }
        }

        public void viewSubtree() {
            viewSubtree(false);
        }

        public void viewNewSubtree() {
            viewSubtree(true);
        }
    }

   

	/** Loads STC data into the STMultipleView. */
    public static TreeDataModel loadData(String filename, TreeDataModel model1) {
        STCReader reader = new STCReader();
        try {
            return reader.readTree(filename, (StdTreeDataModel) model1, null);
        } catch (IOException e) { }
        return null;
    }

    /**
     * The command line access of this demo.  This demo when run from
     * the command line can take the name of any STC file, otherwise it will
     * use the orgchart.stc sample data.
     */
    public static void main(String[] args) {
        final String filename;

        if (args.length < 1 || args[0] == null || args[0].length() == 0)
        	
            //filename = "./res/orgchart.stc";
        	filename = "./res/cuahsitree.stc";
        else
            filename = args[0];
        StdTreeDataModel model1 = new StdTreeDataModel();
        final TreeDataModel tree = loadData(filename, model1);

        

        // ensure AWT thread safety by using invokeLater
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                STMultipleView demo = new STMultipleView(tree);
                demo.setVisible(true);

                if (tree == null)
                    demo.setMessage("Cannot open " + filename + ".");
            }
        }); 
    }
}

class TreeDragSource implements DragSourceListener, DragGestureListener {

    DragSource source;

    DragGestureRecognizer recognizer;

    TransferableTreeNode transferable;

    StdTreeDataNode oldNode;

    
    JTree sourceTree;

    public TreeDragSource(JTree tree, int actions) {
      sourceTree = tree;
      source = new DragSource();
      recognizer = source.createDefaultDragGestureRecognizer(sourceTree,
          actions, this);
    }

    /*
     * Drag Gesture Handler
     */
    public void dragGestureRecognized(DragGestureEvent dge) {
      TreePath path = sourceTree.getSelectionPath();
      if ((path == null) || (path.getPathCount() <= 1)) {
        // We can't move the root node or an empty selection
        return;
      }
      oldNode = (StdTreeDataNode) path.getLastPathComponent();
      
      
      transferable = new TransferableTreeNode(oldNode.toString());
      
      source.startDrag(dge, DragSource.DefaultMoveNoDrop, transferable, this);

      // If you support dropping the node anywhere, you should probably
      // start with a valid move cursor:
      //source.startDrag(dge, DragSource.DefaultMoveDrop, transferable,
      // this);
    }

    /*
     * Drag Event Handlers
     */
    public void dragEnter(DragSourceDragEvent dsde) {
    }

    public void dragExit(DragSourceEvent dse) {
    }

    public void dragOver(DragSourceDragEvent dsde) {
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
      System.out.println("Action: " + dsde.getDropAction());
      System.out.println("Target Action: " + dsde.getTargetActions());
      System.out.println("User Action: " + dsde.getUserAction());
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
      /*
       * to support move or copy, we have to check which occurred:
       */
      System.out.println("Drop Action: " + dsde.getDropAction());
      if (dsde.getDropSuccess()
          && (dsde.getDropAction() == DnDConstants.ACTION_MOVE)) {
    	  StdTreeDataModel model = (StdTreeDataModel) sourceTree.getModel();
    	  //oldNode.removeFromParent();
    	  //parentNode.remove(oldNode);
    	  //model.removeNode(oldNode);
           
      }

      /*
       * to support move only... if (dsde.getDropSuccess()) {
       * ((DefaultTreeModel)sourceTree.getModel()).removeNodeFromParent(oldNode); }
       */
    }
  }
  

class TreeDropTarget implements DropTargetListener {

	  DropTarget target;

	  JTree targetTree;

	  public TreeDropTarget(JTree tree) {
	    targetTree = tree;
	    target = new DropTarget(targetTree, this);
	  }

	  /*
	   * Drop Event Handlers
	   */
	  private TreeNode getNodeForEvent(DropTargetDragEvent dtde) {
	    Point p = dtde.getLocation();
	    DropTargetContext dtc = dtde.getDropTargetContext();
	    JTree tree = (JTree) dtc.getComponent();
	    TreePath path = tree.getClosestPathForLocation(p.x, p.y);
	    return (TreeNode) path.getLastPathComponent();
	  }

	  public void dragEnter(DropTargetDragEvent dtde) {
	    TreeNode node = getNodeForEvent(dtde);
	    if (node.isLeaf()) {
	      dtde.rejectDrag();
	    } else {
	      // start by supporting move operations
	      //dtde.acceptDrag(DnDConstants.ACTION_MOVE);
	      dtde.acceptDrag(dtde.getDropAction());
	    }
	  }

	  public void dragOver(DropTargetDragEvent dtde) {
	    TreeNode node = getNodeForEvent(dtde);
	    if (node.isLeaf()) {
	      dtde.rejectDrag();
	    } else {
	      // start by supporting move operations
	      //dtde.acceptDrag(DnDConstants.ACTION_MOVE);
	      dtde.acceptDrag(dtde.getDropAction());
	    }
	  }

	  public void dragExit(DropTargetEvent dte) {
	  }

	  public void dropActionChanged(DropTargetDragEvent dtde) {
	  }

	  public void drop(DropTargetDropEvent dtde) {
	    Point pt = dtde.getLocation();
	    DropTargetContext dtc = dtde.getDropTargetContext();
	    JTree tree = (JTree) dtc.getComponent();
	    TreePath parentpath = tree.getClosestPathForLocation(pt.x, pt.y);
	    StdTreeDataNode parent = (StdTreeDataNode) parentpath.getLastPathComponent();
	    if (parent.isLeaf()) {
	      dtde.rejectDrop();
	      return;
	    }

	    try {
	      Transferable tr = dtde.getTransferable();
	      DataFlavor[] flavors = tr.getTransferDataFlavors();
	      for (int i = 0; i < flavors.length; i++) {
	        if (tr.isDataFlavorSupported(flavors[i])) {
	          dtde.acceptDrop(dtde.getDropAction());
	          Object p1 = (Object) tr.getTransferData(flavors[i]);
	          //TreePath p = new TreePath((TreePath)p1);
	          //System.out.println("Treepath "+ p.toString());
	          String nodeText = (String) p1;
	          StdTreeDataModel model = (StdTreeDataModel) tree.getModel();
	          /*I don't know a better way to look up the node in the model. 
	           * So I must look at all nodes.
	           */
	        
	        StdTreeDataNode current_node = null;
	  		Enumeration<StdTreeDataNode> iter =  model.getNodes();
	  		current_node = iter.nextElement();
	  		while (!current_node.getText().equals(nodeText)){
	  			current_node = iter.nextElement();
	  			System.out.println(current_node.toString());
	        }
	          model.removeNode(current_node);
	          model.insertChildAt(parent,current_node,0);
	          
	          dtde.dropComplete(true);
	          return;
	        }
	      }
	      dtde.rejectDrop();
	    } catch (Exception e) {
	      e.printStackTrace();
	      dtde.rejectDrop();
	    }
	  }
	}
class TransferableTreeNode implements Transferable {

	  public static DataFlavor TREE_PATH_FLAVOR = new DataFlavor(TreePath.class,
	      "Tree Path");

	  DataFlavor flavors[] = { TREE_PATH_FLAVOR };
	  
	  String path;
	 
	  
	  public TransferableTreeNode(String s){
		  path = s;
	  }
	  
	  public synchronized DataFlavor[] getTransferDataFlavors() {
	    return flavors;
	  }

	  public boolean isDataFlavorSupported(DataFlavor flavor) {
	    return (flavor.getRepresentationClass() == TreePath.class);
	  }

	  public synchronized Object getTransferData(DataFlavor flavor)
	      throws UnsupportedFlavorException, IOException {
	    if (isDataFlavorSupported(flavor)) {
	    	return (Object) path;
	    } else {
	      throw new UnsupportedFlavorException(flavor);
	    }
	  }
	}
