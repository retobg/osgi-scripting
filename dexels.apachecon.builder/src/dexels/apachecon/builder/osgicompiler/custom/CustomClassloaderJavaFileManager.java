package dexels.apachecon.builder.osgicompiler.custom;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author atamur
 * @since 15-Oct-2009
 */

public class CustomClassloaderJavaFileManager extends
		ForwardingJavaFileManager<JavaFileManager> implements JavaFileManager,
		BundleListener {
	private final ClassLoader classLoader;
	private final JavaFileManager standardFileManager;

	private final Map<String, CustomJavaFileFolder> folderMap = new HashMap<String, CustomJavaFileFolder>();
	private final Map<String, CustomJavaFileObject> fileMap = new HashMap<String, CustomJavaFileObject>();
	private BundleContext bundleContext;
	
	private final static Logger logger = LoggerFactory
			.getLogger(CustomClassloaderJavaFileManager.class);

	public CustomClassloaderJavaFileManager(BundleContext context,
			ClassLoader classLoader, JavaFileManager standardFileManager) {
		super(standardFileManager);
		this.classLoader = classLoader;
		this.standardFileManager = standardFileManager;
		this.bundleContext = context;
		this.bundleContext.addBundleListener(this);
	}

	@Override
	public ClassLoader getClassLoader(Location location) {
		return classLoader;
	}

	@Override
	public String inferBinaryName(Location location, JavaFileObject file) {
		if (file instanceof CustomJavaFileObject) {
			String binaryName = ((CustomJavaFileObject) file).binaryName();
			if (binaryName.indexOf('/') >= 0) {
				binaryName = binaryName
						.substring(binaryName.lastIndexOf("/") + 1);
			}
			if (binaryName.indexOf('.') >= 0) {
				binaryName = binaryName.substring(0, binaryName.indexOf('.'));
			}
			return binaryName;
		} else {
			return standardFileManager.inferBinaryName(location, file);
		}
	}

	@Override
	public boolean hasLocation(Location location) {
		return true;
	}

	@Override
	public JavaFileObject getJavaFileForInput(Location location,
			String className, JavaFileObject.Kind kind) throws IOException {
		String binaryName = className.replaceAll("\\.", "/");
		if (kind.equals(Kind.CLASS)) {
			binaryName = binaryName + ".class";
		} else {
			binaryName = binaryName + ".java";
		}
		
		CustomJavaFileObject cjfo = fileMap.get(binaryName);
		if (cjfo != null) {
			return cjfo;
		}
    	String packageName = null;
    	if(className.indexOf("/")>0) {
    		packageName = className.substring(0,className.lastIndexOf("/"));
    	} else {
    		packageName = "";
    	}
    	CustomJavaFileFolder cjf = folderMap.get(packageName);
		if (cjf == null) {
			cjf = new CustomJavaFileFolder(bundleContext,
					packageName);
			folderMap.put(packageName, cjf);
		}

		JavaFileObject jfo = cjf.getFile(binaryName);
		return jfo;
	}

	@Override
	public JavaFileObject getJavaFileForOutput(Location location,
			String className, JavaFileObject.Kind kind, FileObject sibling)
			throws IOException {
		String binaryName = className.replaceAll("\\.", "/") + kind.extension;
		URI uri = URI.create("file:///" + binaryName);
		CustomJavaFileObject cjfo = fileMap.get(binaryName); // new
																// CustomJavaFileObject(binaryName,
																// uri,fileMap.get(uri.toString()));
		if (cjfo == null) {
			cjfo = new CustomJavaFileObject(binaryName, uri,
					(InputStream) null, kind);
			fileMap.put(binaryName, cjfo);

		}
		return cjfo;
	}

	@Override
	public FileObject getFileForInput(Location location, String packageName,
			String relativeName) throws IOException {
		JavaFileObject jf = fileMap.get(location.getName());
		if (jf != null) {
			return jf;
		}
		return super.getFileForInput(location, packageName, relativeName);
	}

	@Override
	public Iterable<JavaFileObject> list(Location location, String packageName,
			Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
		if (location == StandardLocation.PLATFORM_CLASS_PATH) {
			return standardFileManager.list(location, packageName, kinds,
					recurse);
		} else if (location == StandardLocation.CLASS_PATH
				&& kinds.contains(JavaFileObject.Kind.CLASS)) {
				CustomJavaFileFolder folder = folderMap.get(packageName);
				if (folder == null) {
					folder = new CustomJavaFileFolder(bundleContext,
							packageName);
					folderMap.put(packageName, folder);
				}
				return folder.getEntries();
			// }
		}
		return Collections.emptyList();
	}

	@Override
	public int isSupportedOption(String option) {
		return -1;
	}

	@Override
	public void bundleChanged(BundleEvent be) {
		final Bundle bundle = be.getBundle();
		switch (be.getType()) {
			case BundleEvent.UNRESOLVED:
			case BundleEvent.RESOLVED:
				BundleWiring bw = bundle.adapt(BundleWiring.class);
				Iterable<String> pkgs = getAffectedPackages(bw);
				flush(pkgs);
			break;

			
		}

	}

	private void flush(Iterable<String> pkgs) {
		for (String pkg : pkgs) {
			if(folderMap.containsKey(pkg)) {
				logger.info("Flushed package: "+pkg);
			}
			folderMap.remove(pkg);
		}
		
	}

	private Iterable<String> getAffectedPackages(BundleWiring bw) {
		List<String> result = new ArrayList<String>();
		if(bw==null) {
			return result;
		}
		List<BundleCapability> l = bw
				.getCapabilities("osgi.wiring.package");
		for (BundleCapability bundleCapability : l) {
			String pkg = (String) bundleCapability.getAttributes().get(
					"osgi.wiring.package");
			result.add(pkg);
		}
		return result;
	}

	@Override
	public void close() throws IOException {
		super.close();
		bundleContext.removeBundleListener(this);
		fileMap.clear();
	}

}