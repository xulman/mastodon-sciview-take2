import org.scijava.Context;
import sc.iview.SciView;
import sc.iview.ui.MainWindow;

public class Test {

	public static void main(String[] args) {
		final Context ctx = new Context();
		SciView sv = new SciView(ctx);
		MainWindow mainWindow = sv.getMainWindow();
		mainWindow.toggleSidebar();
	}
}
