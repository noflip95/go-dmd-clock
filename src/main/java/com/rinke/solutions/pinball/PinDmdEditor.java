package com.rinke.solutions.pinball;

import java.awt.SplashScreen;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observer;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.rinke.solutions.pinball.animation.AniEvent;
import com.rinke.solutions.pinball.animation.AniWriter;
import com.rinke.solutions.pinball.animation.Animation;
import com.rinke.solutions.pinball.animation.Animation.EditMode;
import com.rinke.solutions.pinball.animation.CompiledAnimation;
import com.rinke.solutions.pinball.animation.EventHandler;
import com.rinke.solutions.pinball.api.BinaryExporter;
import com.rinke.solutions.pinball.api.BinaryExporterFactory;
import com.rinke.solutions.pinball.api.LicenseManager;
import com.rinke.solutions.pinball.api.LicenseManager.Capability;
import com.rinke.solutions.pinball.api.LicenseManagerFactory;
import com.rinke.solutions.pinball.io.ConnectorFactory;
import com.rinke.solutions.pinball.io.FileHelper;
import com.rinke.solutions.pinball.io.Pin2DmdConnector;
import com.rinke.solutions.pinball.io.Pin2DmdConnector.ConnectionHandle;
import com.rinke.solutions.pinball.io.Pin2DmdConnector.UsbCmd;
import com.rinke.solutions.pinball.io.SmartDMDImporter;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.FrameSeq;
import com.rinke.solutions.pinball.model.Mask;
import com.rinke.solutions.pinball.model.PalMapping;
import com.rinke.solutions.pinball.model.PalMapping.SwitchMode;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.PaletteType;
import com.rinke.solutions.pinball.model.Plane;
import com.rinke.solutions.pinball.model.Project;
import com.rinke.solutions.pinball.model.Scene;
import com.rinke.solutions.pinball.swt.ActionAdapter;
import com.rinke.solutions.pinball.swt.CocoaGuiEnhancer;
import com.rinke.solutions.pinball.ui.About;
import com.rinke.solutions.pinball.ui.DeviceConfig;
import com.rinke.solutions.pinball.ui.GifExporter;
import com.rinke.solutions.pinball.ui.RegisterLicense;
import com.rinke.solutions.pinball.ui.UsbConfig;
import com.rinke.solutions.pinball.util.ApplicationProperties;
import com.rinke.solutions.pinball.util.FileChooserUtil;
import com.rinke.solutions.pinball.util.ObservableList;
import com.rinke.solutions.pinball.util.ObservableMap;
import com.rinke.solutions.pinball.util.ObservableProperty;
import com.rinke.solutions.pinball.util.RecentMenuManager;
import com.rinke.solutions.pinball.widget.CircleTool;
import com.rinke.solutions.pinball.widget.ColorizeTool;
import com.rinke.solutions.pinball.widget.DMDWidget;
import com.rinke.solutions.pinball.widget.DrawTool;
import com.rinke.solutions.pinball.widget.FloodFillTool;
import com.rinke.solutions.pinball.widget.LineTool;
import com.rinke.solutions.pinball.widget.PaletteTool;
import com.rinke.solutions.pinball.widget.RectTool;
import com.rinke.solutions.pinball.widget.SetPixelTool;


@Slf4j
public class PinDmdEditor implements EventHandler {

	private static final int FRAME_RATE = 40;
	private static final String HELP_URL = "http://pin2dmd.com/editor/";

	DMD dmd = new DMD(128, 32); // for sake of window builder
	MaskDmdObserver maskDmdObserver;

	AnimationHandler animationHandler = null;

	CyclicRedraw cyclicRedraw = new CyclicRedraw();

	ObservableMap<String, Animation> animations = new ObservableMap<String, Animation>(new LinkedHashMap<>());
	Map<String, DrawTool> drawTools = new HashMap<>();

	Display display;
	protected Shell shell;

	protected int lastTimeCode;

	@Option(name = "-ani", usage = "animation file to load", required = false)
	private String aniToLoad;

	@Option(name = "-cut", usage = "<src name>,<new name>,<start>,<end>", required = false)
	private String cutCmd;

	@Option(name = "-nodirty", usage = "dont check dirty flag on close", required = false)
	private boolean nodirty = false;

	@Option(name = "-save", usage = "if set, project is saved right away", required = false)
	private String saveFile;

	@Option(name = "-load", usage = "if set, project is loaded right away", required = false)
	private String loadFile;

	@Argument
	private java.util.List<String> arguments = new ArrayList<String>();

	private Label lblTcval;
	private Label lblFrameNo;

	private FileChooserUtil fileChooserUtil;

	private String frameTextPrefix = "Pin2dmd Editor ";
	private Animation defaultAnimation = new Animation(null, "", 0, 0, 1, 1, 1);
	ObservableProperty<Animation> selectedAnimation = new ObservableProperty<Animation>(null);
	java.util.List<Animation> playingAnis = new ArrayList<Animation>();
	Palette activePalette;

	// colaboration classes
	DMDClock clock = new DMDClock(false);
	FileHelper fileHelper = new FileHelper();
	SmartDMDImporter smartDMDImporter = new SmartDMDImporter();
	Project project = new Project();
	byte[] emptyMask = new byte[512];

	int numberOfHashes = 4;
	java.util.List<byte[]> hashes = new ArrayList<byte[]>();

	/** instance level SWT widgets */
	Button btnHash[] = new Button[numberOfHashes];
	Text txtDuration;
	Scale scale;
	ComboViewer paletteComboViewer;
	TableViewer aniListViewer;
	TableViewer keyframeTableViewer;
	Button btnRemoveAni;
	Button btnDeleteKeyframe;
	Button btnAddKeyframe;
	Button btnFetchDuration;
	Button btnPrev;
	Button btnNext;
	ComboViewer paletteTypeComboViewer;
	DMDWidget dmdWidget;
	ResourceManager resManager;

	Button btnNewPalette;
	Button btnRenamePalette;
	ToolBar drawToolBar;
	ComboViewer frameSeqViewer;
	Button btnMarkStart;
	Button btnMarkEnd;
	Button btnCut;
	Button btnStartStop;
	Button btnAddFrameSeq;
	DMDWidget previewDmd;
	ObservableList<Animation> frameSeqList = new ObservableList<>(new ArrayList<>());
	//ComboViewer planesComboViewer;

	PaletteTool paletteTool;
	int selectedHashIndex;
	PalMapping selectedPalMapping;
	int saveTimeCode;

	CutInfo cutInfo = new CutInfo();

	java.util.List<Palette> previewPalettes = new ArrayList<>();

	//PlaneNumber planeNumber;
	Label lblPlanesVal;
	Text txtDelayVal;
	private Button btnSortAni;
	LicenseManager licManager;

	private Button btnMask;
	boolean useMask;
	private Observer editAniObserver;
	private Button btnLivePreview;
	private boolean livePreviewActive;
	private ConnectionHandle handle;

	private String pin2dmdAdress = null;

	Pin2DmdConnector connector;

	private Menu menuPopRecentProjects;
	private Menu mntmRecentAnimations;
	private Menu mntmRecentPalettes;

	RecentMenuManager recentProjectsMenuManager;
	RecentMenuManager recentPalettesMenuManager;
	RecentMenuManager recentAnimationsMenuManager;

	private Spinner maskSpinner;
	private int actMaskNumber;
	private Button btnColorMask;
	private Button btnAddColormaskKeyFrame;
	private MenuItem mntmGodmd;

	PaletteHandler paletteHandler;
	AnimationActionHandler aniAction;

	private GoDmdGroup goDmdGroup;
	private MenuItem mntmUploadProject;
	private MenuItem mntmUploadPalettes;
	private Button btnCopyToNext;
	private Button btnUndo;
	private Button btnRedo;
	private Button btnCopyToPrev;
	private String pluginsPath;
	private List<String> loadedPlugins = new ArrayList<>();
	MenuItem mntmSaveProject;
	private String projectFilename;
	private Button btnDeleteColMask;

	public PinDmdEditor() {
		dmd = new DMD(128, 32);
		maskDmdObserver = new MaskDmdObserver();
		maskDmdObserver.setDmd(dmd);
		activePalette = project.palettes.get(0);
		previewPalettes = Palette.previewPalettes();
		licManager = LicenseManagerFactory.getInstance();
		Arrays.fill(emptyMask, (byte) 0xFF);
		pin2dmdAdress = ApplicationProperties.get(ApplicationProperties.PIN2DMD_ADRESS_PROP_KEY);
		checkForPlugins();
		connector = ConnectorFactory.create(pin2dmdAdress);
	}

	private void checkForPlugins() {
		Path currentRelativePath = Paths.get("");
		pluginsPath = currentRelativePath.toAbsolutePath().toString()+File.separator+"plugins";
		String[] fileList = new File(pluginsPath).list((dir, name) -> name.endsWith(".jar"));
		if( fileList!=null) Arrays.stream(fileList).forEach(file -> addSoftwareLibrary(new File(pluginsPath+File.separatorChar+file)));
		try {
			Class.forName("org.bytedeco.javacv.Java2DFrameConverter");
			log.info("successfully loaded video plugin classes");
			loadedPlugins.add("Video");
		} catch (ClassNotFoundException e) {
		}
	}
	
	private  void addSoftwareLibrary(File file) {
		try {
		    Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
		    method.setAccessible(true);
		    method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{file.toURI().toURL()});
		    log.info("adding {} to classpath", file.toURI().toURL());
		} catch( Exception e) {
			log.warn("adding {} to classpath failed", file.getPath());
		}
	}
	
	public void refreshPin2DmdHost(String address) {
		if (address != null && !address.equals(pin2dmdAdress)) {
			if (handle != null) {
				connector.release(handle);
			}
			this.pin2dmdAdress = address;
			ApplicationProperties.put(ApplicationProperties.PIN2DMD_ADRESS_PROP_KEY, pin2dmdAdress);
			connector = ConnectorFactory.create(address);
		}
	}

	/**
	 * handles redraw of animations
	 * 
	 * @author steve
	 */
	private class CyclicRedraw implements Runnable {

		@Override
		public void run() {
			// if( !previewCanvas.isDisposed()) previewCanvas.redraw();
			if (dmdWidget != null && !dmdWidget.isDisposed())
				dmdWidget.redraw();
			if (previewDmd != null && !previewDmd.isDisposed())
				previewDmd.redraw();
			if (animationHandler != null && !animationHandler.isStopped()) {
				animationHandler.run();
				display.timerExec(animationHandler.getRefreshDelay(), cyclicRedraw);
			}
		}
	}

	/**
	 * Launch the application.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Display display = Display.getDefault();
		Realm.runWithDefault(SWTObservables.getRealm(display), new Runnable() {
			public void run() {
				try {
					PinDmdEditor window = new PinDmdEditor();
					window.open(args);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void saveHashes(java.util.List<byte[]> hashes) {
		if (hashes != null) {
			this.hashes.clear();
			for (byte[] h : hashes) {
				this.hashes.add(Arrays.copyOf(h, h.length));
			}
		}
	}

	public void createBindings() {
		// do some bindings
		editAniObserver = ObserverManager.bind(animationHandler, e -> this.enableDrawing(e), () -> animationIsEditable());
		ObserverManager.bind(animationHandler, e -> dmdWidget.setDrawingEnabled(e), () -> animationHandler.isStopped());

		ObserverManager.bind(animationHandler, e -> btnPrev.setEnabled(e), () -> animationHandler.isStopped() && animationHandler.hasAnimations());
		ObserverManager.bind(animationHandler, e -> btnNext.setEnabled(e), () -> animationHandler.isStopped() && animationHandler.hasAnimations());

		ObserverManager.bind(cutInfo, e -> btnCut.setEnabled(e), () -> (cutInfo.getStart() > 0 && cutInfo.getEnd() > 0));

		ObserverManager.bind(cutInfo, e -> btnMarkEnd.setEnabled(e), () -> (cutInfo.getStart() > 0));

		//ObserverManager.bind(animations, e -> btnStartStop.setEnabled(e), () -> !this.animations.isEmpty() && animationHandler.isStopped());
		ObserverManager.bind(animations, e -> btnPrev.setEnabled(e), () -> !this.animations.isEmpty());
		ObserverManager.bind(animations, e -> btnNext.setEnabled(e), () -> !this.animations.isEmpty());
		ObserverManager.bind(animations, e -> btnMarkStart.setEnabled(e), () -> !this.animations.isEmpty());

		ObserverManager.bind(animations, e -> aniListViewer.refresh(), () -> true);
		ObserverManager.bind(animations, e -> buildFrameSeqList(), () -> true);

		// ObserverManager.bind(animations, e->btnAddFrameSeq.setEnabled(e),
		// ()->!frameSeqList.isEmpty());
	}

	private void enableDrawing(boolean e) {
		drawToolBar.setEnabled(e);
		btnColorMask.setEnabled(e);
		btnCopyToNext.setEnabled(e);
		btnCopyToPrev.setEnabled(e);
		btnDeleteColMask.setEnabled(btnColorMask.getSelection());
	}

	private boolean animationIsEditable() {
		return (this.useMask && !project.masks.get(actMaskNumber).locked) || (animationHandler.isStopped() && isEditable(animationHandler.getAnimations()));
	}

	private boolean isEditable(java.util.List<Animation> a) {
		if (a != null) {
			return a.size() == 1 && a.get(0).isMutable();
		}
		return false;
	}

	protected void buildFrameSeqList() {
		frameSeqList.clear();
		frameSeqList.addAll(animations.values().stream().filter(a -> a.isMutable()).collect(Collectors.toList()));
		frameSeqViewer.refresh();
	}

	/**
	 * Open the window.
	 * 
	 * @param args
	 */
	public void open(String[] args) {

		CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();
			System.exit(1);
		}
		
		display = Display.getDefault();
		shell = new Shell();
		fileChooserUtil = new FileChooserUtil(shell);
		paletteHandler = new PaletteHandler(this, shell);
		aniAction = new AnimationActionHandler(this, shell);

		if (SWT.getPlatform().equals("cocoa")) {
			CocoaGuiEnhancer enhancer = new CocoaGuiEnhancer("Pin2dmd Editor");
			enhancer.hookApplicationMenu(display, e -> e.doit = dirtyCheck(),
					new ActionAdapter(() -> new About(shell).open(pluginsPath, loadedPlugins) ),
					new ActionAdapter(() -> new DeviceConfig(shell).open(null)) );
		}
		
		createContents(shell);

		animationHandler = new AnimationHandler(playingAnis, clock, dmd);
		animationHandler.setScale(scale);
		animationHandler.setEventHandler(this);
		animationHandler.setMask(project.mask);
		boolean goDMDenabled = ApplicationProperties.getBoolean(ApplicationProperties.GODMD_ENABLED_PROP_KEY);
		animationHandler.setEnableClock(goDMDenabled);
		
		onNewProject();

		paletteComboViewer.getCombo().select(0);
		paletteTool.setPalette(activePalette);

		createBindings();

		SplashScreen splashScreen = SplashScreen.getSplashScreen();
		if (splashScreen != null) {
			splashScreen.close();
		}

		shell.open();
		shell.layout();
		shell.addListener(SWT.Close, e -> {
			e.doit = dirtyCheck();
		});

		GlobalExceptionHandler.getInstance().setDisplay(display);
		GlobalExceptionHandler.getInstance().setShell(shell);
		new Label(shell, SWT.NONE);
		new Label(shell, SWT.NONE);
		new Label(shell, SWT.NONE);
		new Label(shell, SWT.NONE);

		display.timerExec(animationHandler.getRefreshDelay(), cyclicRedraw);

		processCmdLine();

		int retry = 0;
		while (true) {
			try {
				log.info("entering event loop");
				while (!shell.isDisposed()) {
					if (!display.readAndDispatch()) {
						display.sleep();
					}
				}
				System.exit(0);
			} catch (Exception e) {
				GlobalExceptionHandler.getInstance().showError(e);
				log.error("unexpected error: {}", e);
				if (retry++ > 10)
					System.exit(1);
			}
		}

	}

	private void processCmdLine() {
		// cmd line processing
		if (loadFile != null) {
			loadProject(loadFile);
		}
		if (aniToLoad != null) {
			aniAction.loadAni(aniToLoad, false, true);
		}
		if (cutCmd != null && !animations.isEmpty()) {
			String[] cuts = cutCmd.split(",");
			if (cuts.length >= 3) {
				cutScene(animations.get(cuts[0]), Integer.parseInt(cuts[2]), Integer.parseInt(cuts[3]), cuts[1]);
			}
		}
		if (saveFile != null) {
			saveProject(saveFile);
		}
	}

	private Animation cutScene(Animation animation, int start, int end, String name) {
		Animation cutScene = animation.cutScene(start, end, 4);
		// TODO improve to make it selectable how many planes
		
		paletteHandler.copyPalettePlaneUpgrade();
		
		cutScene.setDesc(name);
		cutScene.setPalIndex(activePalette.index);
		cutScene.setProjectAnimation(true);
		animations.put(name, cutScene);
		aniListViewer.setSelection(new StructuredSelection(cutScene));

		return cutScene;
	}

	void onNewProject() {
		project.clear();
		activePalette = project.palettes.get(0);
		paletteComboViewer.refresh();
		keyframeTableViewer.refresh();
		animations.clear();
		playingAnis.clear();
		selectedAnimation.set(null);
		animationHandler.setAnimations(playingAnis);
		setProjectFilename(null);
	}

	private void onLoadProjectSelected() {
		String filename = fileChooserUtil.choose(SWT.OPEN, null, new String[] { "*.xml;*.json;" }, new String[] { "Project XML", "Project JSON" });
		if (filename != null) {
			loadProject(filename);
		}
	}

	/**
	 * imports a secondary project to implement a merge functionality
	 */
	void onImportProjectSelected() {
		String filename = fileChooserUtil.choose(SWT.OPEN, null, new String[] { "*.xml;*.json;" }, new String[] { "Project XML", "Project JSON" });

		if (filename != null)
			importProject(filename);
	}

	void importProject(String filename) {
		log.info("importing project from {}", filename);
		Project projectToImport = (Project) fileHelper.loadObject(filename);
		// merge into existing Project
		HashSet<String> collisions = new HashSet<>();
		for (String key : projectToImport.frameSeqMap.keySet()) {
			if (project.frameSeqMap.containsKey(key)) {
				collisions.add(key);
			} else {
				project.frameSeqMap.put(key, projectToImport.frameSeqMap.get(key));
			}
		}
		if (!collisions.isEmpty()) {
			MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK | SWT.IGNORE | SWT.ABORT);

			messageBox.setText("Override warning");
			messageBox.setMessage("the following frame seq have NOT been \nimported due to name collisions: " + collisions + "\n");
			messageBox.open();
		}

		for (String inputFile : projectToImport.inputFiles) {
			aniAction.loadAni(buildRelFilename(filename, inputFile), true, true);
		}
		for (PalMapping palMapping : projectToImport.palMappings) {
			project.palMappings.add(palMapping);
		}
	}
	
	protected void setPaletteViewerByIndex(int palIndex) {
		Optional<Palette> optPal = project.palettes.stream().filter(p -> p.index==palIndex).findFirst();
		paletteComboViewer.setSelection(new StructuredSelection(optPal.orElse(activePalette)));
		log.info("setting pal.index to {}",palIndex);
	}
	
	protected void setupUIonProjectLoad() {
		paletteComboViewer.setInput(project.palettes);
		setPaletteViewerByIndex(0);
		keyframeTableViewer.setInput(project.palMappings);
		for (Animation ani : animations.values()) {
			selectedAnimation.set(animations.isEmpty() ? null : ani);
			break;
		}
	}

	void loadProject(String filename) {
		log.info("load project from {}", filename);
		setProjectFilename(filename);
		Project projectToLoad = (Project) fileHelper.loadObject(filename);

		if (projectToLoad != null) {
			shell.setText(frameTextPrefix + " - " + new File(filename).getName());
			project = projectToLoad;
			animations.clear();
			
			// if inputFiles contain project filename remove it
			String aniFilename = replaceExtensionTo("ani", filename);
			project.inputFiles.remove(aniFilename); // full name
			project.inputFiles.remove(new File(aniFilename).getName()); // simple name
			
			for (String file : project.inputFiles) {
				aniAction.loadAni(buildRelFilename(filename, file), true, false);
			}
			
			List<Animation> loadedWithProject = aniAction.loadAni(aniFilename, true, false);
			loadedWithProject.stream().forEach(a->a.setProjectAnimation(true));
			
			setupUIonProjectLoad();
			ensureDefault();
			recentProjectsMenuManager.populateRecent(filename);
		}

	}

	private void ensureDefault() {
		boolean foundDefault = false;
		for (Palette p : project.palettes) {
			if (PaletteType.DEFAULT.equals(p.type)) {
				foundDefault = true;
				break;
			}
		}
		if (!foundDefault) {
			project.palettes.get(0).type = PaletteType.DEFAULT;
		}
	}

	String buildRelFilename(String parent, String file) {
		if( file.contains(File.separator)) return file;
		return new File(parent).getParent() + File.separator + new File(file).getName();
	}
	
	private void onExportRealPinProject() {
		licManager.requireOneOf( Capability.REALPIN, Capability.GODMD);
		String filename = fileChooserUtil.choose(SWT.SAVE, project.name, new String[] { "*.pal" }, new String[] { "Export pal" });
		if (filename != null) {
			warn("Warning", "Please don´t publish projects with copyrighted material / frames");
			exportProject(filename, f -> new FileOutputStream(f), true);
			if( !filename.endsWith("pin2dmd.pal")) {
				warn("Hint", "Remember to rename your export file to pin2dmd.pal if you want to use it" + " in a real pinballs sdcard of pin2dmd.");
			}
		}
	}
	
	private void onExportVirtualPinProject() {
		licManager.requireOneOf(Capability.VPIN, Capability.GODMD);
		String filename = fileChooserUtil.choose(SWT.SAVE, project.name, new String[] { "*.pal" }, new String[] { "Export pal" });
		if (filename != null) {
			warn("Warning", "Please don´t publish projects with copyrighted material / frames");
			exportProject(filename, f -> new FileOutputStream(f), false);
		}
	}

	void onSaveProjectSelected(boolean saveAs) {
		if( saveAs ) {
			String filename = fileChooserUtil.choose(SWT.SAVE, project.name, new String[] { "*.xml" }, new String[] { "Project XML" });
			if (filename != null)
				saveProject(filename);
		} else {
			saveProject(getProjectFilename());
		}
	}

	@FunctionalInterface
	public interface OutputStreamProvider {
		OutputStream buildStream(String name) throws IOException;
	}

	void onUploadProjectSelected() {
		Map<String, ByteArrayOutputStream> captureOutput = new HashMap<>();
		exportProject("a.dat", f -> {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			captureOutput.put(f, stream);
			return stream;
		}, true);

		connector.transferFile("pin2dmd.pal", new ByteArrayInputStream(captureOutput.get("a.dat").toByteArray()));
		if (captureOutput.containsKey("a.fsq")) {
			connector.transferFile("pin2dmd.fsq", new ByteArrayInputStream(captureOutput.get("a.fsq").toByteArray()));
		}
		sleep(1500);
		connector.sendCmd(UsbCmd.RESET);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
		}
	}

	void exportProject(String filename, OutputStreamProvider streamProvider, boolean realPin) {

		licManager.requireOneOf(Capability.VPIN, Capability.REALPIN, Capability.GODMD);

		// rebuild frame seq map	
		project.frameSeqMap.clear();
		for (PalMapping p : project.palMappings) {
			if (p.frameSeqName != null) {
				FrameSeq frameSeq = new FrameSeq(p.frameSeqName);
				if (p.switchMode.equals(SwitchMode.ADD)) {
					frameSeq.mask = 0b11111100;
				}
				project.frameSeqMap.put(p.frameSeqName, frameSeq);
			}
		}
		
		// VPIN
		if( !realPin ) {
			List<Animation> anis = new ArrayList<>();
			for (FrameSeq p : project.frameSeqMap.values()) {
				Animation ani = animations.get(p.name);
				// copy without extending frames
				CompiledAnimation cani = (CompiledAnimation) ani.cutScene(ani.start, ani.end, 0);
				cani.actFrame = 0;
				cani.setDesc(ani.getDesc());
				DMD tmp = new DMD(128, 32);
				for (int i = cani.start; i <= cani.end; i++) {
					Frame f = cani.render(tmp, false);
					for( int j = 0; j < f.planes.size(); j++) {
						if (((1 << j) & p.mask) == 0) {
							Arrays.fill(f.planes.get(j).plane, (byte)0);
						}
					}
				}
				anis.add(cani);
			}
			if( !anis.isEmpty() ) {
				String aniFilename = replaceExtensionTo("ani", filename);
				AniWriter aniWriter = new AniWriter(anis, aniFilename, 3, project.palettes, null);
				aniWriter.setHeader("VPIN");
				try {
					BinaryExporter exporter = BinaryExporterFactory.getInstance();
					DataOutputStream dos2 = new DataOutputStream(streamProvider.buildStream(filename));
					// for vpins version is 2
					project.version = 2;
					exporter.writeTo(dos2, aniWriter.getOffsetMap(), project);
					dos2.close();
				} catch (IOException e) {
					throw new RuntimeException("error writing " + filename, e);
				}
			}
			
		} else {
			// for all referenced frame mapping we must also copy the frame data as
			// there are two models
			for (FrameSeq p : project.frameSeqMap.values()) {
				Animation ani = animations.get(p.name);
				
				ani.actFrame = 0;
				DMD tmp = new DMD(128, 32);
				for (int i = 0; i <= ani.end; i++) {
					Frame frame = new Frame( ani.render(tmp, false) ); // copy frames to not remove in org
					// remove planes not in mask
					int pl = 0;
					for (Iterator<Plane> iter = frame.planes.iterator(); iter.hasNext();) {
						iter.next();
						if (((1 << pl) & p.mask) == 0) {
							iter.remove();
						}
						pl++;
					}
					p.frames.add(frame);
				}
			}
			// create addtional files for frame sequences
			try {
				Map<String, Integer> map = new HashMap<String, Integer>();
				BinaryExporter exporter = BinaryExporterFactory.getInstance();
				if (!project.frameSeqMap.isEmpty()) {
					DataOutputStream dos = new DataOutputStream(streamProvider.buildStream(replaceExtensionTo("fsq", filename)));
					map = exporter.writeFrameSeqTo(dos, project);
					dos.close();
				}

				project.version = 1;
				DataOutputStream dos2 = new DataOutputStream(streamProvider.buildStream(filename));
				exporter.writeTo(dos2, map, project);
				dos2.close();
				// fileHelper.storeObject(project, filename);
			} catch (IOException e) {
				throw new RuntimeException("error writing " + filename, e);
			}
		}		
	}

	void saveProject(String filename) {
		log.info("write project to {}", filename);
		String aniFilename = replaceExtensionTo("ani", filename);
		String baseName = new File(aniFilename).getName();
		String baseNameWithoutExtension = baseName.substring(0, baseName.indexOf('.'));
		if (project.name == null) {
			project.name = baseNameWithoutExtension;
		} else if (!project.name.equals(baseNameWithoutExtension)) {
			// save as
			project.inputFiles.remove(project.name + ".ani");
		}
		
		// we need to "tag" the projects animations that are always stored in the projects ani file
		// the project ani file is not included in the inputFile list but animations gets loaded
		// implicitly
		
		String path = new File(filename).getParent(); 
		// so first check directly included anis in project inputfiles
		for( String inFile : project.inputFiles) {
			Optional<Animation> optAni = animations.values().stream().filter(a -> a.getName().equals(path+File.separator+inFile)).findFirst();
			optAni.ifPresent(a-> {if( a.isDirty()) {
				aniAction.storeAnimations(Arrays.asList(a), a.getName(), 3, false);
				a.setDirty(false);
			}});
		}
		
		storeOrDeleteProjectAnimations(aniFilename);

		Map<String,FrameSeq> frameSeqMapSave = project.frameSeqMap;
		project.frameSeqMap = null; // remove this for saving
		fileHelper.storeObject(project, filename);
		project.frameSeqMap = frameSeqMapSave;
		project.dirty = false;
	}

	private Pair<Integer, Map<String, Integer>> storeOrDeleteProjectAnimations(String aniFilename) {
		// only need to save ani's that are 'project' animations
		List<Animation> prjAnis = animations.values().stream().filter(a->a.isProjectAnimation()).collect(Collectors.toList());
		if( !prjAnis.isEmpty() ) {
			return aniAction.storeAnimations(prjAnis, aniFilename, 3, true);
		} else {
			new File(aniFilename).delete(); // delete project ani file
			return null;
		}
	}

	String replaceExtensionTo(String newExt, String filename) {
		int p = filename.lastIndexOf(".");
		if (p != -1)
			return filename.substring(0, p) + "." + newExt;
		return filename;
	}

	public void createHashButtons(Composite parent, int x, int y) {
		for (int i = 0; i < numberOfHashes; i++) {
			btnHash[i] = new Button(parent, SWT.CHECK);
			if (i == 0)
				btnHash[i].setSelection(true);
			btnHash[i].setData(Integer.valueOf(i));
			btnHash[i].setText("Hash" + i);
			// btnHash[i].setFont(new Font(shell.getDisplay(), "sans", 10, 0));
			btnHash[i].setBounds(x, y + i * 16, 331, 18);
			btnHash[i].addListener(SWT.Selection, e -> {
				selectedHashIndex = (Integer) e.widget.getData();
				if (selectedPalMapping != null) {
					selectedPalMapping.hashIndex = selectedHashIndex;
				}
				for (int j = 0; j < numberOfHashes; j++) {
					if (j != selectedHashIndex)
						btnHash[j].setSelection(false);
				}
				// switch palettes in preview
					previewDmd.setPalette(previewPalettes.get(selectedHashIndex));
				});
		}
	}

	public void onAddKeyFrameClicked(SwitchMode switchMode) {
		PalMapping palMapping = new PalMapping(activePalette.index, "KeyFrame " + (project.palMappings.size() + 1));
		if (selectedHashIndex != -1) {
			palMapping.setDigest(hashes.get(selectedHashIndex));
		}
		palMapping.animationName = selectedAnimation.get().getDesc();
		palMapping.frameIndex = selectedAnimation.get().actFrame;
		palMapping.switchMode = switchMode;
		if (useMask) {
			palMapping.withMask = useMask;
			palMapping.maskNumber = actMaskNumber;
			project.masks.get(actMaskNumber).locked = true;
			onMaskChecked(true);
		}

		if (!checkForDuplicateKeyFrames(palMapping)) {
			project.palMappings.add(palMapping);
			saveTimeCode = lastTimeCode;
			keyframeTableViewer.refresh();
		} else {
			warn("Hash is already used", "The selected hash is already used by another key frame");
		}
	}

	boolean checkForDuplicateKeyFrames(PalMapping palMapping) {
		for (PalMapping p : project.palMappings) {
			if (Arrays.equals(p.crc32, palMapping.crc32))
				return true;
		}
		return false;
	}
	
	/**
	 * Create contents of the window.
	 */
	void createContents(Shell shell) {
		shell.setSize(1238, 657);
		shell.setText("Pin2dmd - Editor");
		shell.setLayout(new GridLayout(4, false));

		createMenu(shell);
		
		setProjectFilename(null);

		recentProjectsMenuManager = new RecentMenuManager("recentProject", 4, menuPopRecentProjects, e -> loadProject((String) e.widget.getData()));
		recentProjectsMenuManager.loadRecent();

		recentPalettesMenuManager = new RecentMenuManager("recentPalettes", 4, mntmRecentPalettes, e -> paletteHandler.loadPalette((String) e.widget.getData()));
		recentPalettesMenuManager.loadRecent();

		recentAnimationsMenuManager = new RecentMenuManager("recentAnimations", 4, mntmRecentAnimations, e -> aniAction.loadAni(((String) e.widget.getData()),
				true, false));
		recentAnimationsMenuManager.loadRecent();

		resManager = new LocalResourceManager(JFaceResources.getResources(), shell);

		Label lblAnimations = new Label(shell, SWT.NONE);
		lblAnimations.setText("Animations");

		Label lblKeyframes = new Label(shell, SWT.NONE);
		lblKeyframes.setText("KeyFrames");

		Label lblPreview = new Label(shell, SWT.NONE);
		lblPreview.setText("Preview");
		new Label(shell, SWT.NONE);

		aniListViewer = new TableViewer(shell, SWT.BORDER | SWT.V_SCROLL);
		Table aniList = aniListViewer.getTable();
		GridData gd_aniList = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_aniList.heightHint = 231;
		gd_aniList.widthHint = 189;
		aniList.setLayoutData(gd_aniList);
		aniList.setLinesVisible(true);
		aniList.addKeyListener(new EscUnselect(aniListViewer));
		aniListViewer.setContentProvider(ArrayContentProvider.getInstance());
		aniListViewer.setLabelProvider(new LabelProviderAdapter(o -> ((Animation) o).getDesc()));
		aniListViewer.setInput(animations.values());
		aniListViewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			onAnimationSelectionChanged(selection.size() > 0 ? (Animation) selection.getFirstElement() : null);
		});
		TableViewerColumn viewerCol1 = new TableViewerColumn(aniListViewer, SWT.LEFT);
		viewerCol1.setEditingSupport(new GenericTextCellEditor(aniListViewer, e -> ((Animation) e).getDesc(), (e, v) -> {
			Animation ani = (Animation) e;
			updateAnimationMapKey(ani.getDesc(), v);
			ani.setDesc(v);
			frameSeqViewer.refresh();
		}));

		viewerCol1.getColumn().setWidth(220);
		viewerCol1.setLabelProvider(new IconLabelProvider(shell, o -> ((Animation) o).getIconAndText() ));

		keyframeTableViewer = new TableViewer(shell, SWT.SINGLE | SWT.V_SCROLL);
		Table keyframeList = keyframeTableViewer.getTable();
		GridData gd_keyframeList = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_keyframeList.heightHint = 231;
		gd_keyframeList.widthHint = 137;
		keyframeList.setLinesVisible(true);
		keyframeList.setLayoutData(gd_keyframeList);
		keyframeList.addKeyListener(new EscUnselect(keyframeTableViewer));

		//keyframeTableViewer.setLabelProvider(new KeyframeLabelProvider(shell));
		keyframeTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		keyframeTableViewer.setInput(project.palMappings);
		keyframeTableViewer.addSelectionChangedListener(event -> onKeyframeChanged(event));

		TableViewerColumn viewerColumn = new TableViewerColumn(keyframeTableViewer, SWT.LEFT);
		viewerColumn.setEditingSupport(new GenericTextCellEditor(keyframeTableViewer, e -> ((PalMapping) e).name, (e, v) -> {
			((PalMapping) e).name = v;
		}));

		viewerColumn.getColumn().setWidth(200);
		viewerColumn.setLabelProvider(new IconLabelProvider(shell, o -> Pair.of(
				((PalMapping)o).switchMode.name().toLowerCase(), ((PalMapping)o).name ) ));

		dmdWidget = new DMDWidget(shell, SWT.DOUBLE_BUFFERED, this.dmd, true);
		// dmdWidget.setBounds(0, 0, 700, 240);
		GridData gd_dmdWidget = new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1);
		gd_dmdWidget.heightHint = 231;
		gd_dmdWidget.widthHint = 826;
		dmdWidget.setLayoutData(gd_dmdWidget);
		dmdWidget.setPalette(activePalette);
		dmdWidget.addListeners(l -> onFrameChanged(l));

		Composite composite_1 = new Composite(shell, SWT.NONE);
		composite_1.setLayout(new GridLayout(2, false));
		GridData gd_composite_1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_composite_1.heightHint = 35;
		gd_composite_1.widthHint = 206;
		composite_1.setLayoutData(gd_composite_1);

		btnRemoveAni = new Button(composite_1, SWT.NONE);
		btnRemoveAni.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		btnRemoveAni.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
			}
		});
		btnRemoveAni.setText("Remove");
		btnRemoveAni.setEnabled(false);
		btnRemoveAni.addListener(SWT.Selection, e -> {
			if (selectedAnimation.isPresent()) {
				Animation a = selectedAnimation.get();
				String key = a.getDesc();
				if( a.isProjectAnimation() ) project.dirty = true;
				animations.remove(key);
				playingAnis.clear();
				animationHandler.setAnimations(playingAnis);
				animationHandler.setClockActive(true);
			}
		});

		btnSortAni = new Button(composite_1, SWT.NONE);
		btnSortAni.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		btnSortAni.setText("Sort");
		btnSortAni.addListener(SWT.Selection, e -> onSortAnimations());

		Composite composite_2 = new Composite(shell, SWT.NONE);
		composite_2.setLayout(new GridLayout(3, false));
		GridData gd_composite_2 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_composite_2.heightHint = 35;
		gd_composite_2.widthHint = 157;
		composite_2.setLayoutData(gd_composite_2);

		btnDeleteKeyframe = new Button(composite_2, SWT.NONE);
		GridData gd_btnDeleteKeyframe = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_btnDeleteKeyframe.widthHint = 88;
		btnDeleteKeyframe.setLayoutData(gd_btnDeleteKeyframe);
		btnDeleteKeyframe.setText("Remove");
		btnDeleteKeyframe.setEnabled(false);
		btnDeleteKeyframe.addListener(SWT.Selection, e -> {
			if (selectedPalMapping != null) {
				project.palMappings.remove(selectedPalMapping);
				keyframeTableViewer.refresh();
				checkReleaseMask();
			}
		});

		Button btnSortKeyFrames = new Button(composite_2, SWT.NONE);
		btnSortKeyFrames.setText("Sort");
		btnSortKeyFrames.addListener(SWT.Selection, e -> onSortKeyFrames());
		new Label(composite_2, SWT.NONE);

		scale = new Scale(shell, SWT.NONE);
		scale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		scale.addListener(SWT.Selection, e -> animationHandler.setPos(scale.getSelection()));

		Group grpKeyframe = new Group(shell, SWT.NONE);
		grpKeyframe.setLayout(new GridLayout(3, false));
		GridData gd_grpKeyframe = new GridData(SWT.FILL, SWT.TOP, false, false, 2, 4);
		gd_grpKeyframe.heightHint = 191;
		gd_grpKeyframe.widthHint = 350;
		grpKeyframe.setLayoutData(gd_grpKeyframe);
		grpKeyframe.setText("KeyFrames");

		Composite composite_hash = new Composite(grpKeyframe, SWT.NONE);
		//gd_composite_hash.widthHint = 105;
		GridData gd_composite_hash = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1);
		gd_composite_hash.widthHint = 148;
		composite_hash.setLayoutData(gd_composite_hash);
		createHashButtons(composite_hash, 10, 0);
		
		previewDmd = new DMDWidget(grpKeyframe, SWT.DOUBLE_BUFFERED, dmd, false);
		GridData gd_dmdPreWidget = new GridData(SWT.CENTER, SWT.TOP, false, false, 1, 1);
		gd_dmdPreWidget.heightHint = 40;
		gd_dmdPreWidget.widthHint = 132;
		previewDmd.setLayoutData(gd_dmdPreWidget);
		previewDmd.setDrawingEnabled(false);
		previewDmd.setPalette(previewPalettes.get(0));
				
		new Label(grpKeyframe, SWT.NONE);
		
		btnAddColormaskKeyFrame = new Button(grpKeyframe, SWT.NONE);
		btnAddColormaskKeyFrame.setToolTipText("Adds a key frame that trigger a color masking scene to be overlayed");
		btnAddColormaskKeyFrame.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnAddColormaskKeyFrame.setText("Add ColorMask");
		btnAddColormaskKeyFrame.setEnabled(false);
		btnAddColormaskKeyFrame.addListener(SWT.Selection, e -> onAddFrameSeqClicked(SwitchMode.ADD));

		btnAddKeyframe = new Button(grpKeyframe, SWT.NONE);
		btnAddKeyframe.setToolTipText("Adds a key frame that switches palette");
		btnAddKeyframe.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false, 1, 1));
		btnAddKeyframe.setText("Add PalSwitch");
		btnAddKeyframe.setEnabled(false);
		btnAddKeyframe.addListener(SWT.Selection, e -> onAddKeyFrameClicked(SwitchMode.PALETTE));

		Label lblDuration = new Label(grpKeyframe, SWT.NONE);
		lblDuration.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblDuration.setText("Duration:");

		txtDuration = new Text(grpKeyframe, SWT.BORDER);
		GridData gd_txtDuration = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_txtDuration.widthHint = 93;
		txtDuration.setLayoutData(gd_txtDuration);
		txtDuration.setText("0");
		txtDuration.addListener(SWT.Verify, e -> e.doit = Pattern.matches("^[0-9]+$", e.text));
		txtDuration.addListener(SWT.Modify, e -> {
			if (selectedPalMapping != null) {
				selectedPalMapping.durationInMillis = Integer.parseInt(txtDuration.getText());
				selectedPalMapping.durationInFrames = (int) selectedPalMapping.durationInMillis / 40;
			}
		});
		
		btnFetchDuration = new Button(grpKeyframe, SWT.NONE);
		btnFetchDuration.setToolTipText("Fetches duration for palette switches by calculating the difference between actual timestamp and keyframe timestamp");
		btnFetchDuration.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnFetchDuration.setText("Fetch Duration");
		btnFetchDuration.setEnabled(false);
		btnFetchDuration.addListener(SWT.Selection, e -> {
			if (selectedPalMapping != null) {
				selectedPalMapping.durationInMillis = lastTimeCode - saveTimeCode;
				selectedPalMapping.durationInFrames = (int) selectedPalMapping.durationInMillis / FRAME_RATE;
				txtDuration.setText(selectedPalMapping.durationInMillis + "");
			}
		});
				
		Label lblNewLabel = new Label(grpKeyframe, SWT.NONE);
		lblNewLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblNewLabel.setText("FrameSeq:");

		frameSeqViewer = new ComboViewer(grpKeyframe, SWT.NONE);
		Combo frameSeqCombo = frameSeqViewer.getCombo();
		frameSeqCombo.setToolTipText("Choose frame sequence to use with key frame");
		GridData gd_frameSeqCombo = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_frameSeqCombo.widthHint = 100;
		frameSeqCombo.setLayoutData(gd_frameSeqCombo);
		frameSeqViewer.setLabelProvider(new LabelProviderAdapter(o -> ((Animation) o).getDesc()));
		frameSeqViewer.setContentProvider(ArrayContentProvider.getInstance());
		frameSeqViewer.setInput(frameSeqList);
		frameSeqViewer.addSelectionChangedListener(event -> onFrameSeqChanged(event));

		btnAddFrameSeq = new Button(grpKeyframe, SWT.NONE);
		btnAddFrameSeq.setToolTipText("Adds a keyframe that triggers playback of a replacement scene");
		btnAddFrameSeq.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnAddFrameSeq.setText("Add FrameSeq");
		btnAddFrameSeq.addListener(SWT.Selection, e -> onAddFrameSeqClicked(SwitchMode.REPLACE));
		btnAddFrameSeq.setEnabled(false);

		Group grpDetails = new Group(shell, SWT.NONE);
		grpDetails.setLayout(new GridLayout(10, false));
		GridData gd_grpDetails = new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1);
		gd_grpDetails.heightHint = 21;
		gd_grpDetails.widthHint = 776;
		grpDetails.setLayoutData(gd_grpDetails);
		grpDetails.setText("Details");

		Label lblFrame = new Label(grpDetails, SWT.NONE);
		lblFrame.setText("Frame:");

		lblFrameNo = new Label(grpDetails, SWT.NONE);
		GridData gd_lblFrameNo = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblFrameNo.widthHint = 66;
		gd_lblFrameNo.minimumWidth = 60;
		lblFrameNo.setLayoutData(gd_lblFrameNo);
		lblFrameNo.setText("---");

		Label lblTimecode = new Label(grpDetails, SWT.NONE);
		lblTimecode.setText("Timecode:");

		lblTcval = new Label(grpDetails, SWT.NONE);
		GridData gd_lblTcval = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblTcval.widthHint = 62;
		gd_lblTcval.minimumWidth = 80;
		lblTcval.setLayoutData(gd_lblTcval);
		lblTcval.setText("---");

		Label lblDelay = new Label(grpDetails, SWT.NONE);
		lblDelay.setText("Delay:");

		txtDelayVal = new Text(grpDetails, SWT.NONE);
		GridData gd_lblDelayVal = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblDelayVal.widthHint = 53;
		txtDelayVal.setLayoutData(gd_lblDelayVal);
		txtDelayVal.setText("");
		txtDelayVal.addListener(SWT.Modify, e-> {
			String val = txtDelayVal.getText();
			int delay = StringUtils.isEmpty(val)?0:Integer.parseInt(val);
			if( selectedAnimation.isPresent() ) {
				Animation ani = selectedAnimation.get();
				if( ani.isMutable() && ani instanceof CompiledAnimation ) {
					CompiledAnimation cani = (CompiledAnimation)ani;
					if( actFrameOfSelectedAni<cani.frames.size() ) {
						cani.frames.get(actFrameOfSelectedAni).delay = delay;
					}
					project.dirty = true;
				}
			}
		} );
		txtDelayVal.addListener(SWT.Verify, e -> e.doit = Pattern.matches("^[0-9]*$", e.text));

		Label lblPlanes = new Label(grpDetails, SWT.NONE);
		lblPlanes.setText("Planes:");

		lblPlanesVal = new Label(grpDetails, SWT.NONE);
		lblPlanesVal.setText("---");
		new Label(grpDetails, SWT.NONE);

		btnLivePreview = new Button(grpDetails, SWT.CHECK);
		btnLivePreview.setToolTipText("controls live preview to real display device");
		btnLivePreview.setText("Live Preview");
		btnLivePreview.addListener(SWT.Selection, e -> switchLivePreview(e));

		Composite composite = new Composite(shell, SWT.NONE);
		composite.setLayout(new GridLayout(9, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

		btnStartStop = new Button(composite, SWT.NONE);
		btnStartStop.setText("Start");
		btnStartStop.addListener(SWT.Selection, e -> onStartStopClicked(animationHandler.isStopped()));

		btnPrev = new Button(composite, SWT.NONE);
		btnPrev.setText("<");
		btnPrev.addListener(SWT.Selection, e -> onPrevFrameClicked());

		btnNext = new Button(composite, SWT.NONE);
		btnNext.setText(">");
		btnNext.addListener(SWT.Selection, e -> onNextFrameClicked());

		btnMarkStart = new Button(composite, SWT.NONE);
		btnMarkStart.setToolTipText("Marks start of scene for cutting");
		btnMarkEnd = new Button(composite, SWT.NONE);
		btnCut = new Button(composite, SWT.NONE);
		btnCut.setToolTipText("Cuts out a new scene for editing and use a replacement or color mask");

		btnMarkStart.setText("Mark Start");
		btnMarkStart.addListener(SWT.Selection, e -> {
			cutInfo.setStart(selectedAnimation.get().actFrame);
		});

		btnMarkEnd.setText("Mark End");
		btnMarkEnd.addListener(SWT.Selection, e -> {
			cutInfo.setEnd(selectedAnimation.get().actFrame);
		});

		btnCut.setText("Cut");
		btnCut.addListener(SWT.Selection, e -> {
			// respect number of planes while cutting / copying
				Animation ani = cutScene(selectedAnimation.get(), cutInfo.getStart(), cutInfo.getEnd(), buildUniqueName(animations));
				log.info("cutting out scene from {} to {}", cutInfo);
				cutInfo.reset();
			});

		new Label(composite, SWT.NONE);

		Button btnIncPitch = new Button(composite, SWT.NONE);
		btnIncPitch.setText("+");
		btnIncPitch.addListener(SWT.Selection, e -> dmdWidget.incPitch());

		Button btnDecPitch = new Button(composite, SWT.NONE);
		btnDecPitch.setText("-");
		btnDecPitch.addListener(SWT.Selection, e -> dmdWidget.decPitch());

		Group grpPalettes = new Group(shell, SWT.NONE);
		grpPalettes.setLayout(new GridLayout(5, false));
		GridData gd_grpPalettes = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_grpPalettes.widthHint = 505;
		gd_grpPalettes.heightHint = 71;
		grpPalettes.setLayoutData(gd_grpPalettes);
		grpPalettes.setText("Palettes");

		paletteComboViewer = new ComboViewer(grpPalettes, SWT.NONE);
		Combo combo = paletteComboViewer.getCombo();
		GridData gd_combo = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_combo.widthHint = 166;
		combo.setLayoutData(gd_combo);
		paletteComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		paletteComboViewer.setLabelProvider(new LabelProviderAdapter(o -> ((Palette) o).index + " - " + ((Palette) o).name));
		paletteComboViewer.setInput(project.palettes);
		paletteComboViewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			if (selection.size() > 0) {
				onPaletteChanged((Palette) selection.getFirstElement());
			}
		});

		paletteTypeComboViewer = new ComboViewer(grpPalettes, SWT.READ_ONLY);
		Combo combo_1 = paletteTypeComboViewer.getCombo();
		combo_1.setToolTipText("Type of palette. Default palette is choosen at start and after timed switch is expired");
		GridData gd_combo_1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_combo_1.widthHint = 96;
		combo_1.setLayoutData(gd_combo_1);
		paletteTypeComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		paletteTypeComboViewer.setInput(PaletteType.values());
		paletteTypeComboViewer.setSelection(new StructuredSelection(activePalette.type));
		paletteTypeComboViewer.addSelectionChangedListener(e -> onPaletteTypeChanged(e));
						
		Button btnApplyPalette = new Button(grpPalettes, SWT.NONE);
		btnApplyPalette.setText("Apply");
		btnApplyPalette.addListener(SWT.Selection, e -> onApplyPalette(activePalette));
		
		btnNewPalette = new Button(grpPalettes, SWT.NONE);
		btnNewPalette.setToolTipText("Creates a new palette by copying the actual colors");
		btnNewPalette.setText("New");
		btnNewPalette.addListener(SWT.Selection, e -> paletteHandler.newPalette());

		btnRenamePalette = new Button(grpPalettes, SWT.NONE);
		btnRenamePalette.setToolTipText("Confirms the new palette name");
		btnRenamePalette.setText("Rename");
		btnRenamePalette.addListener(SWT.Selection, e -> {
			String newName = paletteComboViewer.getCombo().getText();
			if (newName.contains(" - ")) {
				activePalette.name = newName.split(" - ")[1];
				setPaletteViewerByIndex(activePalette.index);
				paletteComboViewer.refresh();
			} else {
				warn("Illegal palette name", "Palette names must consist of palette index and name.\nName format therefore must be '<idx> - <name>'");
				paletteComboViewer.getCombo().setText(activePalette.index + " - " + activePalette.name);
			}

		});

		Composite grpPal = new Composite(grpPalettes, SWT.NONE);
		grpPal.setLayout(new GridLayout(1, false));
		GridData gd_grpPal = new GridData(SWT.LEFT, SWT.TOP, false, false, 3, 1);
		gd_grpPal.widthHint = 333;
		gd_grpPal.heightHint = 22;
		grpPal.setLayoutData(gd_grpPal);
		// GridData gd_grpPal = new GridData(SWT.LEFT, SWT.CENTER, false, false,
		// 1, 1);
		// gd_grpPal.widthHint = 223;
		// gd_grpPal.heightHint = 61;
		// grpPal.setLayoutData(gd_grpPal);
		//
		paletteTool = new PaletteTool(shell, grpPal, SWT.FLAT | SWT.RIGHT, activePalette);

		paletteTool.addListener(dmdWidget);
								
		Label lblCtrlclickToEdit = new Label(grpPalettes, SWT.NONE);
		GridData gd_lblCtrlclickToEdit = new GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1);
		gd_lblCtrlclickToEdit.widthHint = 131;
		lblCtrlclickToEdit.setLayoutData(gd_lblCtrlclickToEdit);
		lblCtrlclickToEdit.setText("Ctrl-Click to edit color");
		new Label(grpPalettes, SWT.NONE);
		new Label(grpPalettes, SWT.NONE);
		new Label(grpPalettes, SWT.NONE);
		new Label(grpPalettes, SWT.NONE);
		new Label(grpPalettes, SWT.NONE);
		
		Composite composite_3 = new Composite(shell, SWT.NONE);
		GridLayout gl_composite_3 = new GridLayout(1, false);
		gl_composite_3.marginWidth = 0;
		gl_composite_3.marginHeight = 0;
		composite_3.setLayout(gl_composite_3);
		GridData gd_composite_3 = new GridData(SWT.RIGHT, SWT.FILL, false, false, 1, 2);
		gd_composite_3.heightHint = 190;
		gd_composite_3.widthHint = 338;
		composite_3.setLayoutData(gd_composite_3);
		goDmdGroup = new GoDmdGroup(composite_3);

		Group grpDrawing = new Group(shell, SWT.NONE);
		grpDrawing.setLayout(new GridLayout(5, false));
		GridData gd_grpDrawing = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_grpDrawing.heightHint = 63;
		gd_grpDrawing.widthHint = 505;
		grpDrawing.setLayoutData(gd_grpDrawing);
		grpDrawing.setText("Drawing");

		drawToolBar = new ToolBar(grpDrawing, SWT.FLAT | SWT.RIGHT);
		drawToolBar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));

		ToolItem tltmPen = new ToolItem(drawToolBar, SWT.RADIO);
		tltmPen.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/pencil.png")));
		tltmPen.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("pencil")));

		ToolItem tltmFill = new ToolItem(drawToolBar, SWT.RADIO);
		tltmFill.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/color-fill.png")));
		tltmFill.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("fill")));

		ToolItem tltmRect = new ToolItem(drawToolBar, SWT.RADIO);
		tltmRect.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/rect.png")));
		tltmRect.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("rect")));

		ToolItem tltmLine = new ToolItem(drawToolBar, SWT.RADIO);
		tltmLine.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/line.png")));
		tltmLine.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("line")));

		ToolItem tltmCircle = new ToolItem(drawToolBar, SWT.RADIO);
		tltmCircle.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/oval.png")));
		tltmCircle.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("circle")));

		ToolItem tltmColorize = new ToolItem(drawToolBar, SWT.RADIO);
		tltmColorize.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/colorize.png")));
		tltmColorize.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("colorize")));
		drawTools.put("pencil", new SetPixelTool(paletteTool.getSelectedColor()));
		drawTools.put("fill", new FloodFillTool(paletteTool.getSelectedColor()));
		drawTools.put("rect", new RectTool(paletteTool.getSelectedColor()));
		drawTools.put("line", new LineTool(paletteTool.getSelectedColor()));
		drawTools.put("circle", new CircleTool(paletteTool.getSelectedColor()));
		drawTools.put("colorize", new ColorizeTool(paletteTool.getSelectedColor()));
		drawTools.values().forEach(d -> paletteTool.addIndexListener(d));
		paletteTool.addListener(palette -> {
			if (livePreviewActive) {
				connector.upload(activePalette, handle);
			}
		});
		
		btnColorMask = new Button(grpDrawing, SWT.CHECK);
		btnColorMask.setToolTipText("limits drawing to upper planes, so that this will just add coloring layers");
		btnColorMask.setText("ColMask");
		btnColorMask.addListener(SWT.Selection, e -> onColorMaskChecked(btnColorMask.getSelection()));
		//btnColorMask.add

		Label lblMaskNo = new Label(grpDrawing, SWT.NONE);
		lblMaskNo.setText("Mask No:");

		maskSpinner = new Spinner(grpDrawing, SWT.BORDER);
		maskSpinner.setToolTipText("select the mask to use");
		maskSpinner.setMinimum(0);
		maskSpinner.setMaximum(9);
		maskSpinner.addListener(SWT.Selection, e -> onMaskNumberChanged(e));
		
		btnMask = new Button(grpDrawing, SWT.CHECK);
		GridData gd_btnMask = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_btnMask.widthHint = 93;
		btnMask.setLayoutData(gd_btnMask);
		btnMask.setText("Show Mask");
		btnMask.addListener(SWT.Selection, e -> onMaskChecked(btnMask.getSelection()));
		
		btnCopyToPrev = new Button(grpDrawing, SWT.NONE);
		btnCopyToPrev.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		btnCopyToPrev.setText("CopyToPrev");
		btnCopyToPrev.addListener(SWT.Selection, e->onCopyAndMoveToPrevFrameClicked());
		
		btnCopyToNext = new Button(grpDrawing, SWT.NONE);
		btnCopyToNext.setToolTipText("copy the actual scene / color mask to next frame and move forward");
		btnCopyToNext.setText("CopyToNext");
		btnCopyToNext.addListener(SWT.Selection, e->onCopyAndMoveToNextFrameClicked());
		
		btnUndo = new Button(grpDrawing, SWT.NONE);
		btnUndo.setText("&Undo");
		btnUndo.addListener(SWT.Selection, e -> onUndoClicked());
		
		btnRedo = new Button(grpDrawing, SWT.NONE);
		btnRedo.setText("&Redo");
		btnRedo.addListener(SWT.Selection, e -> onRedoClicked());
		
		btnDeleteColMask = new Button(grpDrawing, SWT.NONE);
		btnDeleteColMask.setText("Delete");
		btnDeleteColMask.setEnabled(false);
		btnDeleteColMask.addListener(SWT.Selection, e -> onDeleteColMaskClicked());

		ObserverManager.bind(maskDmdObserver, e -> btnUndo.setEnabled(e), () -> maskDmdObserver.canUndo());
		ObserverManager.bind(maskDmdObserver, e -> btnRedo.setEnabled(e), () -> maskDmdObserver.canRedo());

	}

	/**
	 * creates a unique key name for scenes
	 * @param anis the map containing the keys
	 * @return the new unique name
	 */
	String buildUniqueName(ObservableMap<String, Animation> anis) {
		int no = anis.size();
		String name = "Scene " + no;
		while( anis.containsKey(name)) {
			no++;
			name = "Scene " + no;
		}
		return name;
	}

	private void onDeleteColMaskClicked() {
		dmd.addUndoBuffer();
		Frame frame = dmd.getFrame();
		// delete plane 2 und 3
		if( frame.planes.size() == 4) {
			Arrays.fill( frame.planes.get(2).plane, (byte)0 );
			Arrays.fill( frame.planes.get(3).plane, (byte)0 );
		}
		dmdRedraw();
	}

	private void onStartStopClicked(boolean stopped) {
		if( stopped )
		{
			selectedAnimation.ifPresent(a->a.commitDMDchanges(dmd));
			animationHandler.start();
			display.timerExec(animationHandler.getRefreshDelay(), cyclicRedraw);
			btnStartStop.setText("Stop");
		} else {
			animationHandler.stop();
			btnStartStop.setText("Start");
		}
	}

	private void onPrevFrameClicked() {
		selectedAnimation.ifPresent(a->a.commitDMDchanges(dmd));
		animationHandler.prev();
	}
	
	private void onNextFrameClicked() {
		selectedAnimation.ifPresent(a->a.commitDMDchanges(dmd));
		animationHandler.next();
	}
	
	private void onCopyAndMoveToNextFrameClicked() {
		onNextFrameClicked();
		CompiledAnimation ani = (CompiledAnimation) selectedAnimation.get();
		if( !ani.hasEnded() ) {
			copyFrame(ani, ani.getActFrame(), -1);
		}
	}
	
	private void onCopyAndMoveToPrevFrameClicked() {
		onPrevFrameClicked();
		CompiledAnimation ani = (CompiledAnimation) selectedAnimation.get();
		if( ani.getActFrame() >= ani.getStart() ) {
			copyFrame(ani, ani.getActFrame(), 1);
		}
	}

	private void copyFrame(CompiledAnimation cani, int n, int offset) {
		Frame srcFrame = cani.frames.get(n+offset);
		Frame actFrame = cani.frames.get(n);
		int drawMask = dmd.getDrawMask();
		dmd.addUndoBuffer();
		for( int i = 0; i < actFrame.planes.size(); i++) {
			if( (drawMask&1) != 0) {
				int size = srcFrame.planes.get(i).plane.length;
				System.arraycopy(
						srcFrame.planes.get(i).plane, 0,
						dmd.getFrame().planes.get(i).plane, 0, size);
				dmdRedraw();
			}
			drawMask >>= 1;
		}
	}

	private void onSortKeyFrames() {
		Collections.sort(project.palMappings, new Comparator<PalMapping>() {
			@Override
			public int compare(PalMapping o1, PalMapping o2) {
				return o1.name.compareTo(o2.name);
			}
		});
		keyframeTableViewer.refresh();
	}

	private void onColorMaskChecked(boolean on) {
		dmd.setDrawMask(on ? 0b11111100 : 0xFFFF);
		selectedAnimation.get().setEditMode( on ? EditMode.MASK: EditMode.REPLACE);
		aniListViewer.refresh();
		btnDeleteColMask.setEnabled(on);
	}

	/**
	 * checks all pal mappings and releases masks if not used anymore
	 */
	private void checkReleaseMask() {
		HashSet<Integer> useMasks = new HashSet<>();
		for (PalMapping p : project.palMappings) {
			if (p.withMask) {
				useMasks.add(p.maskNumber);
			}
		}
		for (int i = 0; i < project.masks.size(); i++) {
			project.masks.get(i).locked = useMasks.contains(i);
		}
		onMaskChecked(useMask);
	}
	
	// TODO !!! make selected animation observable to bind change handler to it (maybe remove) Optional
	// make this the general change handler, and let the click handler only set selected animation

	private void onAnimationSelectionChanged(Animation a) {
		log.info("onAnimationSelectionChanged: {}", a);
		Animation current = selectedAnimation.get();
		if(a!= null && current != null && a.getDesc().equals(current.getDesc())) return;
		
		if( selectedAnimation.isPresent() && current.isMutable()) {
			goDmdGroup.updateAnimation(current);
		}
		if (a != null) {
			selectedAnimation.set(a);
			int numberOfPlanes = selectedAnimation.get().getRenderer().getNumberOfPlanes();
			if (numberOfPlanes == 3) {
				numberOfPlanes = 2;
				goDmdGroup.transitionCombo.select(1);
			} else {
				goDmdGroup.transitionCombo.select(0);
			}
			btnColorMask.setSelection(a.getEditMode()==EditMode.MASK);
			onColorMaskChecked(a.getEditMode()==EditMode.MASK);// doesnt fire event?????
			dmd.setNumberOfSubframes(numberOfPlanes);
			paletteTool.setNumberOfPlanes(useMask?1:numberOfPlanes);
			//planesComboViewer.setSelection(new StructuredSelection(PlaneNumber.valueOf(numberOfPlanes)));
			playingAnis.clear();
			playingAnis.add(selectedAnimation.get());
			animationHandler.setAnimations(playingAnis);
			if(a.isMutable() )
				setPaletteViewerByIndex(selectedAnimation.get().getPalIndex());
			dmdRedraw();
		} else {
			selectedAnimation.set(null);
		}
		goDmdGroup.updateAniModel(selectedAnimation.get());
		btnRemoveAni.setEnabled(a != null);
		btnAddKeyframe.setEnabled(a != null);
	}
	
	private void onApplyPalette(Palette selectedPalette) {
		if (selectedPalMapping != null) {
			selectedPalMapping.palIndex = activePalette.index;
			log.info("change index in Keyframe {} to {}", selectedPalMapping.name, activePalette.index);
		}
		// change palette in ANI file
		if (selectedAnimation.get().isMutable()) {
			selectedAnimation.get().setPalIndex(activePalette.index);
		}
		
	}

	private void onPaletteChanged(Palette newPalette) {
		activePalette = newPalette;
		dmdWidget.setPalette(activePalette);
		paletteTool.setPalette(activePalette);
		log.info("new palette is {}", activePalette);
		paletteTypeComboViewer.setSelection(new StructuredSelection(activePalette.type));
		if (livePreviewActive)
			connector.switchToPal(activePalette.index, handle);
	}

	void updateAnimationMapKey(String oldKey, String newKey) {
		ArrayList<Animation> tmp = new ArrayList<Animation>();
		if (!oldKey.equals(newKey)) {
			animations.values().forEach(ani -> tmp.add(ani));
			animations.clear();
			tmp.forEach(ani -> animations.put(ani.getDesc(), ani));
		}
	}

	private void onFrameChanged(Frame frame) {
		if (livePreviewActive) {
			connector.sendFrame(frame, handle);
		}
	}

	private void switchLivePreview(Event e) {
		boolean selection = btnLivePreview.getSelection();
		if (selection) {
			try {
				connector.switchToMode(DeviceMode.PinMame_RGB.ordinal(), null);
				handle = connector.connect(pin2dmdAdress);
				livePreviewActive = selection;
				for( Palette pal : project.palettes ) {
					connector.upload(pal,handle);
				}
				setEnableUsbTooling(!selection);
			} catch (RuntimeException ex) {
				warn("usb problem", "Message was: " + ex.getMessage());
				btnLivePreview.setSelection(false);
			}
		} else {
			if (handle != null) {
				try {
					connector.release(handle);
					livePreviewActive = selection;
					setEnableUsbTooling(!selection);
				} catch (RuntimeException ex) {
					warn("usb problem", "Message was: " + ex.getMessage());
				}
				handle = null;
			}
		}

	}

	private void setEnableUsbTooling(boolean enabled) {
		mntmUploadPalettes.setEnabled(enabled);
		mntmUploadProject.setEnabled(enabled);
	}

	private void onAddFrameSeqClicked(SwitchMode switchMode) {
		if (!frameSeqViewer.getSelection().isEmpty()) {
			if (selectedHashIndex != -1) {
				Animation ani = (Animation) ((IStructuredSelection) frameSeqViewer.getSelection()).getFirstElement();
				// TODO add index, add ref to framesSeq
				PalMapping palMapping = new PalMapping(0, "KeyFrame " + ani.getDesc());
				palMapping.setDigest(hashes.get(selectedHashIndex));
				palMapping.palIndex = activePalette.index;
				palMapping.frameSeqName = ani.getDesc();
				palMapping.animationName = selectedAnimation.get().getDesc();
				palMapping.switchMode = switchMode;
				palMapping.frameIndex = selectedAnimation.get().actFrame;
				if (useMask) {
					palMapping.withMask = useMask;
					palMapping.maskNumber = actMaskNumber;
					project.masks.get(actMaskNumber).locked = true;
					onMaskChecked(true);
				}
				if (!checkForDuplicateKeyFrames(palMapping)) {
					project.palMappings.add(palMapping);
					keyframeTableViewer.refresh();
				} else {
					warn("duplicate hash", "There is already another Keyframe that uses the same hash");
				}
			} else {
				warn("no hash selected", "in order to create a key frame mapping, you must select a hash");
			}
		}
	}

	private void warn(String header, String msg) {
		MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
		messageBox.setText(header);
		messageBox.setMessage(msg);
		messageBox.open();
	}

	private void onMaskChecked(boolean useMask) {
		this.useMask = useMask;
		Mask maskToUse = project.masks.get(maskSpinner.getSelection());
		if (useMask) {
			activateMask(maskToUse);
		} else {
			deactivateMask(maskToUse);
		}
		editAniObserver.update(animationHandler, null);
	}

	private void deactivateMask(Mask mask) {
		DMD dmdMask = dmdWidget.getMask();
		if (dmdMask != null)
			System.arraycopy(dmdMask.getFrame().planes.get(0).plane, 0, mask.data, 0, mask.data.length);
		int planes = dmd.getNumberOfPlanes();
		paletteTool.setNumberOfPlanes(planes);
		dmdWidget.setMask(null, false);
		maskDmdObserver.setMask(null);
		animationHandler.setMask(emptyMask);
	}

	void activateMask(Mask mask) {
		DMD maskDMD = new DMD(128, 32);
		Frame frame = new Frame();
		frame.planes.add(new Plane((byte) 0, mask.data));
		maskDMD.setFrame(frame);
		paletteTool.setNumberOfPlanes(1);
		dmdWidget.setMask(maskDMD, mask.locked);
		maskDmdObserver.setMask(maskDMD);
		animationHandler.setMask(mask.data);
	}

	void onMaskNumberChanged(Event e) {
		int newMaskNumber = ((Spinner) e.widget).getSelection();
		if (useMask && newMaskNumber != actMaskNumber) {
			log.info("mask number changed {} -> {}", actMaskNumber, newMaskNumber);
			deactivateMask(project.masks.get(actMaskNumber));
			activateMask(project.masks.get(newMaskNumber));
			actMaskNumber = newMaskNumber;
			editAniObserver.update(animationHandler, null);
		}
	}

	private void onSortAnimations() {
		ArrayList<Entry<String, Animation>> list = new ArrayList<>(animations.entrySet());
		Collections.sort(list, new Comparator<Entry<String, Animation>>() {

			@Override
			public int compare(Entry<String, Animation> o1, Entry<String, Animation> o2) {
				return o1.getValue().getDesc().compareTo(o2.getValue().getDesc());
			}
		});
		animations.clear();
		for (Entry<String, Animation> entry : list) {
			animations.put(entry.getKey(), entry.getValue());
		}
	}

	private void dmdRedraw() {
		dmdWidget.redraw();
		previewDmd.redraw();
	}
	
	void onFrameSeqChanged(SelectionChangedEvent event) {
		IStructuredSelection selection = (IStructuredSelection) event.getSelection();
		btnAddFrameSeq.setEnabled(selection.size() > 0);
		btnAddColormaskKeyFrame.setEnabled(selection.size() > 0);
	}

	void onKeyframeChanged(SelectionChangedEvent event) {
		IStructuredSelection selection = (IStructuredSelection) event.getSelection();
		if (selection.size() > 0) {
			if (((PalMapping) selection.getFirstElement()).equals(selectedPalMapping)) {
				keyframeTableViewer.setSelection(StructuredSelection.EMPTY);
				selectedPalMapping = null;
				return;
			}
			// set new mapping
			selectedPalMapping = (PalMapping) selection.getFirstElement();

			log.debug("selected new palMapping {}", selectedPalMapping);

			selectedHashIndex = selectedPalMapping.hashIndex;

			// current firmware always checks with and w/o mask
			// btnMask.setSelection(selectedPalMapping.withMask);
			// btnMask.notifyListeners(SWT.Selection, new Event());

			txtDuration.setText(selectedPalMapping.durationInMillis + "");
			setPaletteViewerByIndex(selectedPalMapping.palIndex);
			
			for (int j = 0; j < numberOfHashes; j++) {
				btnHash[j].setSelection(j == selectedHashIndex);
			}

			selectedAnimation.set(animations.get(selectedPalMapping.animationName));
			aniListViewer.setSelection(new StructuredSelection(selectedAnimation.get()));

			if (selectedPalMapping.frameSeqName != null)
				frameSeqViewer.setSelection(new StructuredSelection(animations.get(selectedPalMapping.frameSeqName)));

			animationHandler.setPos(selectedPalMapping.frameIndex);

			if (selectedPalMapping.withMask) {
				String txt = btnHash[selectedHashIndex].getText();
				btnHash[selectedHashIndex].setText("M" + selectedPalMapping.maskNumber + " " + txt);
			}

			saveTimeCode = (int) selectedAnimation.get().getTimeCode(selectedPalMapping.frameIndex);
		} else {
			selectedPalMapping = null;
		}
		btnDeleteKeyframe.setEnabled(selection.size() > 0);
		btnFetchDuration.setEnabled(selection.size() > 0);
	}

	void onPaletteTypeChanged(SelectionChangedEvent e) {
		IStructuredSelection selection = (IStructuredSelection) e.getSelection();
		PaletteType palType = (PaletteType) selection.getFirstElement();
		activePalette.type = palType;
		if (PaletteType.DEFAULT.equals(palType)) {
			for (int i = 0; i < project.palettes.size(); i++) {
				if (i != activePalette.index) { // set previous default to
												// normal
					if (project.palettes.get(i).type.equals(PaletteType.DEFAULT)) {
						project.palettes.get(i).type = PaletteType.NORMAL;
					}
					;
				}
			}
		}
	}

	/**
	 * check if dirty.
	 * 
	 * @return true, if not dirty or if user decides to ignore dirtyness (or
	 *         global ignore flag is set via cmdline)
	 */
	boolean dirtyCheck() {
		if (project.dirty && !nodirty) {
			MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK | SWT.CANCEL);

			messageBox.setText("Unsaved Changes");
			messageBox.setMessage("There are unsaved changes in project. Proceed?");
			int res = messageBox.open();
			return (res == SWT.OK);
		} else {
			return true;
		}
	}

	/**
	 * creates the top level menu
	 */
	private void createMenu(Shell shell) {
		Menu menu = new Menu(shell, SWT.BAR);
		shell.setMenuBar(menu);

		MenuItem mntmfile = new MenuItem(menu, SWT.CASCADE);
		mntmfile.setText("&File");

		Menu menu_1 = new Menu(mntmfile);
		mntmfile.setMenu(menu_1);

		MenuItem mntmNewProject = new MenuItem(menu_1, SWT.NONE);
		mntmNewProject.setText("New Project");
		mntmNewProject.addListener(SWT.Selection, e -> {
			if (dirtyCheck()) {
				onNewProject();
			}
		});

		MenuItem mntmLoadProject = new MenuItem(menu_1, SWT.NONE);
		mntmLoadProject.addListener(SWT.Selection, e -> onLoadProjectSelected());
		mntmLoadProject.setText("Load Project");

		mntmSaveProject = new MenuItem(menu_1, SWT.NONE);
		mntmSaveProject.setText("Save Project");
		mntmSaveProject.addListener(SWT.Selection, e -> onSaveProjectSelected(false));

		MenuItem mntmSaveAsProject = new MenuItem(menu_1, SWT.NONE);
		mntmSaveAsProject.setText("Save Project as");
		mntmSaveAsProject.addListener(SWT.Selection, e -> onSaveProjectSelected(true));

		MenuItem mntmRecentProjects = new MenuItem(menu_1, SWT.CASCADE);
		mntmRecentProjects.setText("Recent Projects");

		menuPopRecentProjects = new Menu(mntmRecentProjects);
		mntmRecentProjects.setMenu(menuPopRecentProjects);

		new MenuItem(menu_1, SWT.SEPARATOR);

		MenuItem mntmImportProject = new MenuItem(menu_1, SWT.NONE);
		mntmImportProject.setText("Import Project");
		mntmImportProject.addListener(SWT.Selection, e -> onImportProjectSelected());

		MenuItem mntmExportRealPinProject = new MenuItem(menu_1, SWT.NONE);
		mntmExportRealPinProject.setText("Export Project (real pin)");
		mntmExportRealPinProject.addListener(SWT.Selection, e -> onExportRealPinProject());

		MenuItem mntmExportVpinProject = new MenuItem(menu_1, SWT.NONE);
		mntmExportVpinProject.setText("Export Project (virt pin)");
		mntmExportVpinProject.addListener(SWT.Selection, e -> onExportVirtualPinProject());

		mntmUploadProject = new MenuItem(menu_1, SWT.NONE);
		mntmUploadProject.setText("Upload Project");
		mntmUploadProject.addListener(SWT.Selection, e -> onUploadProjectSelected());

		new MenuItem(menu_1, SWT.SEPARATOR);

		MenuItem mntmExit = new MenuItem(menu_1, SWT.NONE);
		mntmExit.setText("Exit");
		mntmExit.addListener(SWT.Selection, e -> {
			if (dirtyCheck()) {
				shell.close();
				shell.dispose();
			}
		});

		MenuItem mntmedit = new MenuItem(menu, SWT.CASCADE);
		mntmedit.setText("&Edit");

		Menu menu_5 = new Menu(mntmedit);
		mntmedit.setMenu(menu_5);

		MenuItem mntmUndo = new MenuItem(menu_5, SWT.NONE);
		mntmUndo.setText("Undo");
		mntmUndo.addListener(SWT.Selection, e -> onUndoClicked());
		ObserverManager.bind(maskDmdObserver, e -> mntmUndo.setEnabled(e), () -> maskDmdObserver.canUndo());

		MenuItem mntmRedo = new MenuItem(menu_5, SWT.NONE);
		mntmRedo.setText("Redo");
		mntmRedo.addListener(SWT.Selection, e -> onRedoClicked());
		ObserverManager.bind(maskDmdObserver, e -> mntmRedo.setEnabled(e), () -> maskDmdObserver.canRedo());

		MenuItem mntmAnimations = new MenuItem(menu, SWT.CASCADE);
		mntmAnimations.setText("&Animations");

		Menu menu_2 = new Menu(mntmAnimations);
		mntmAnimations.setMenu(menu_2);

		MenuItem mntmLoadAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmLoadAnimation.setText("Load Animation(s)");
		mntmLoadAnimation.addListener(SWT.Selection, e -> aniAction.onLoadAniWithFC(true));

		MenuItem mntmSaveAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmSaveAnimation.setText("Save Animation(s)");
		mntmSaveAnimation.addListener(SWT.Selection, e -> aniAction.onSaveAniWithFC(1));

		MenuItem mntmSaveAniExt = new MenuItem(menu_2, SWT.NONE);
		mntmSaveAniExt.setText("Save Animation(s) v2");
		mntmSaveAniExt.addListener(SWT.Selection, e -> aniAction.onSaveAniWithFC(2));
		
		MenuItem mntmSaveAniExt1 = new MenuItem(menu_2, SWT.NONE);
		mntmSaveAniExt1.setText("Save Animation(s) v3");
		mntmSaveAniExt1.addListener(SWT.Selection, e -> aniAction.onSaveAniWithFC(3));
		
		MenuItem mntmSaveSingleAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmSaveSingleAnimation.setText("Save single Animation");
		mntmSaveSingleAnimation.addListener(SWT.Selection, e -> aniAction.onSaveSingleAniWithFC(1));

		MenuItem mntmRecentAnimationsItem = new MenuItem(menu_2, SWT.CASCADE);
		mntmRecentAnimationsItem.setText("Recent Animations");

		mntmRecentAnimations = new Menu(mntmRecentAnimationsItem);
		mntmRecentAnimationsItem.setMenu(mntmRecentAnimations);

		new MenuItem(menu_2, SWT.SEPARATOR);

		MenuItem mntmExportAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmExportAnimation.setText("Export Animation as GIF");
		mntmExportAnimation.addListener(SWT.Selection, e -> {
			GifExporter exporter = new GifExporter(shell, activePalette, playingAnis.get(0));
			exporter.open();
		});

		MenuItem mntmpalettes = new MenuItem(menu, SWT.CASCADE);
		mntmpalettes.setText("&Palettes / Mode");
		Menu menu_3 = new Menu(mntmpalettes);
		mntmpalettes.setMenu(menu_3);

		MenuItem mntmLoadPalette = new MenuItem(menu_3, SWT.NONE);
		mntmLoadPalette.setText("Load Palette");
		mntmLoadPalette.addListener(SWT.Selection, e -> paletteHandler.loadPalette());

		MenuItem mntmSavePalette = new MenuItem(menu_3, SWT.NONE);
		mntmSavePalette.setText("Save Palette");
		mntmSavePalette.addListener(SWT.Selection, e -> paletteHandler.savePalette());

		MenuItem mntmRecentPalettesItem = new MenuItem(menu_3, SWT.CASCADE);
		mntmRecentPalettesItem.setText("Recent Palettes");

		mntmRecentPalettes = new Menu(mntmRecentPalettesItem);
		mntmRecentPalettesItem.setMenu(mntmRecentPalettes);

		new MenuItem(menu_3, SWT.SEPARATOR);

		mntmUploadPalettes = new MenuItem(menu_3, SWT.NONE);
		mntmUploadPalettes.setText("Upload Palettes");
		mntmUploadPalettes.addListener(SWT.Selection, e -> connector.upload(activePalette));

		new MenuItem(menu_3, SWT.SEPARATOR);

		MenuItem mntmDevice = new MenuItem(menu_3, SWT.NONE);
		mntmDevice.setText("Create Device File / WiFi");
		mntmDevice.addListener(SWT.Selection, e -> {
			DeviceConfig deviceConfig = new DeviceConfig(shell);
			deviceConfig.open(pin2dmdAdress);
			refreshPin2DmdHost(deviceConfig.getPin2DmdHost());
		});

		MenuItem mntmUsbconfig = new MenuItem(menu_3, SWT.NONE);
		mntmUsbconfig.setText("Configure Device via USB");
		mntmUsbconfig.addListener(SWT.Selection, e -> new UsbConfig(shell).open());

		MenuItem mntmhelp = new MenuItem(menu, SWT.CASCADE);
		mntmhelp.setText("&Help");

		Menu menu_4 = new Menu(mntmhelp);
		mntmhelp.setMenu(menu_4);

		MenuItem mntmGetHelp = new MenuItem(menu_4, SWT.NONE);
		mntmGetHelp.setText("Get help");
		mntmGetHelp.addListener(SWT.Selection, e -> Program.launch(HELP_URL));

		mntmGodmd = new MenuItem(menu_4, SWT.CHECK);
		mntmGodmd.setText("goDMD Panel");
		mntmGodmd.addListener(SWT.Selection, e -> {
			boolean goDMDenabled = mntmGodmd.getSelection();
			goDmdGroup.grpGoDMDCrtls.setVisible(goDMDenabled);
			animationHandler.setEnableClock(goDMDenabled);
			ApplicationProperties.put(ApplicationProperties.GODMD_ENABLED_PROP_KEY, Boolean.toString(goDMDenabled));
		});
		mntmGodmd.setSelection(ApplicationProperties.getBoolean(ApplicationProperties.GODMD_ENABLED_PROP_KEY));

		MenuItem mntmRegister = new MenuItem(menu_4, SWT.NONE);
		mntmRegister.setText("Register");
		mntmRegister.addListener(SWT.Selection, e -> new RegisterLicense(shell).open());

		new MenuItem(menu_4, SWT.SEPARATOR);

		MenuItem mntmAbout = new MenuItem(menu_4, SWT.NONE);
		mntmAbout.setText("About");
		mntmAbout.addListener(SWT.Selection, e -> new About(shell).open(pluginsPath, loadedPlugins));
	}

	private void onRedoClicked() {
		maskDmdObserver.redo();
		dmdRedraw();
	}

	private void onUndoClicked() {
		maskDmdObserver.undo();
		dmdRedraw();
	}

	public String getPrintableHashes(byte[] p) {
		StringBuffer hexString = new StringBuffer();
		for (int j = 0; j < p.length; j++)
			hexString.append(String.format("%02X", p[j]));
		return hexString.toString();
	}
	
	int actFrameOfSelectedAni = 0;


	@Override
	public void notifyAni(AniEvent evt) {
		switch (evt.evtType) {
		case ANI:
			lblFrameNo.setText("" + evt.actFrame);
			actFrameOfSelectedAni = evt.actFrame;
			lblTcval.setText("" + evt.timecode);
			txtDelayVal.setText("" + evt.delay);
			lblPlanesVal.setText("" + evt.nPlanes);
			// hashLabel.setText(
			int i = 0;
			for (byte[] p : evt.hashes) {
				String hash = getPrintableHashes(p);
				if (hash.startsWith("B2AA7578" /* "BF619EAC0CDF3F68D496EA9344137E8B" */)) { // disable
																								// for
																								// empty
																								// frame
					btnHash[i].setText("");
					btnHash[i].setEnabled(false);
				} else {
					btnHash[i].setText(hash);
					btnHash[i].setEnabled(true);
				}
				i++;
				if (i >= btnHash.length)
					break;
			}
			while (i < 4) {
				btnHash[i].setText("");
				btnHash[i].setEnabled(false);
				i++;
			}

			saveHashes(evt.hashes);
			lastTimeCode = evt.timecode;
			if (livePreviewActive && evt.frame != null) {
				connector.sendFrame(evt.frame, handle);
			}
			break;
		case CLOCK:
			lblFrameNo.setText("");
			lblTcval.setText("");
			// sourceList.deselectAll();
			for (int j = 0; j < 4; j++)
				btnHash[j++].setText(""); // clear hashes
			break;
		case CLEAR:
			for (int j = 0; j < 4; j++)
				btnHash[j++].setText(""); // clear hashes
			if (livePreviewActive) {
				connector.sendFrame(new Frame(), handle);
			}
			break;
		}
		dmdRedraw();
	}

	public String getProjectFilename() {
		return projectFilename;
	}

	public void setProjectFilename(String projectFilename) {
		mntmSaveProject.setEnabled(projectFilename!=null);
		this.projectFilename = projectFilename;
	}
}
