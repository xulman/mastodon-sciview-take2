import sc.iview.SciView;
import sc.iview.ui.MainWindow;

public class Test {

	public static void main(String[] args) {
		/*
		SciView sv = new SciView("Vlado window",800,600);
		sv.waitForSceneInitialisation();
		MainWindow mainWindow = sv.getMainWindow();
		mainWindow.toggleSidebar();
		*/
		try {
			SciView sv = SciView.create();
		} catch (Exception e) {
			System.out.println("got exception: "+e.getMessage());
		}
	}
}
