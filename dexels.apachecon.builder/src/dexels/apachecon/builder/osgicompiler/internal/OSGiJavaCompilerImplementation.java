package dexels.apachecon.builder.osgicompiler.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.apache.commons.io.IOUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import dexels.apachecon.builder.osgicompiler.OSGiJavaCompiler;
import dexels.apachecon.builder.osgicompiler.custom.CustomClassLoader;
import dexels.apachecon.builder.osgicompiler.custom.CustomClassloaderJavaFileManager;
import dexels.apachecon.builder.osgicompiler.custom.CustomJavaFileObject;

@Component(immediate=true,configurationPolicy=ConfigurationPolicy.IGNORE)
public class OSGiJavaCompilerImplementation implements OSGiJavaCompiler {

	
	private final static Logger logger = LoggerFactory
			.getLogger(OSGiJavaCompilerImplementation.class);
	private BundleContext context;
	private StandardJavaFileManager fileManager;
	private CustomClassloaderJavaFileManager customJavaFileManager;
	private JavaCompiler compiler;
//	private DiagnosticListener<JavaFileObject> compilerOutputListener;
	private ServiceRegistration<JavaFileManager> fileManagerRegistration;
	private CustomClassLoader customClassLoader;
	private ServiceRegistration<ClassLoader> customClassLoaderRegistration;
	DiagnosticListener<JavaFileObject> compilerOutputListener;
	
	public OSGiJavaCompilerImplementation() {
		
	}
	
	
	@Activate
	public void activateCompiler(BundleContext c) {
			logger.info("Activating java compiler.");
			this.context = c; // c.getBundleContext();
			compiler = ToolProvider.getSystemJavaCompiler();
			compilerOutputListener = new DiagnosticListener<JavaFileObject>() {

				@Override
				public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
					logger.info("Problem in filemanager: "+diagnostic.getMessage(Locale.ENGLISH));
				}
			};
			fileManager = compiler.getStandardFileManager(compilerOutputListener, null, null);
			customJavaFileManager = new CustomClassloaderJavaFileManager(context, getClass().getClassLoader(), fileManager);
			this.customClassLoader = new CustomClassLoader(customJavaFileManager);
			
			this.fileManagerRegistration = this.context.registerService(JavaFileManager.class, customJavaFileManager, null);
			
			Dictionary<String, String> nsc = new Hashtable<String, String>();
			nsc.put("type", "navajoScriptClassLoader");
			this.customClassLoaderRegistration = this.context.registerService(ClassLoader.class, customClassLoader, nsc);
			logger.info("Java compiler online.");
	} 

	@Deactivate
	public void deactivate() {
		logger.info("Deactivating java compiler");
		try {
			customJavaFileManager.close();
		} catch (IOException e) {
			logger.error("Error closing custom file manager",e);
		}
		if(fileManagerRegistration!=null) {
			fileManagerRegistration.unregister();
		}
		if(customClassLoaderRegistration!=null) {
			customClassLoaderRegistration.unregister();
		}
	}
	
	@Override
	public byte[] compile(String className, InputStream source) throws IOException {
		 JavaFileObject javaSource = getJavaSourceFileObject(className, source);
		 Iterable<? extends JavaFileObject> fileObjects = Arrays.asList(javaSource);
		 final Writer sw = new StringWriter();
		 DiagnosticListener<JavaFileObject> compilerOutputListener = new DiagnosticListener<JavaFileObject>() {

				@Override
				public void report(Diagnostic<? extends JavaFileObject> jfo) {
					try {
						sw.write("Compilation problem: "+jfo.getMessage(Locale.ENGLISH)+"\n");
					} catch (IOException e) {
						logger.error("Compilation problem: ",e);
					}
					
				}
				
			};
		CompilationTask task = compiler.getTask(null, customJavaFileManager, compilerOutputListener,new ArrayList<String>(), null, fileObjects);
		task.call();
		CustomJavaFileObject jfo = (CustomJavaFileObject) customJavaFileManager.getJavaFileForInput(StandardLocation.CLASS_OUTPUT, "script/"+className, Kind.CLASS);
		if(jfo==null) {
			logger.error("Compilation failed: \n"+sw.toString() +" for compilation unit: "+className);
			return null;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy(jfo.openInputStream(),baos);
		return baos.toByteArray();
	}
	
//	private void test() throws IOException {
//		byte[] jfo = compile("mathtest/Calculator",getExampleCode());
//		if (jfo==null) {
//			logger.error("compilation failed.");
//		} else {
//			logger.info("compilation ok: "+jfo.length);
//		}
//	}

//	private InputStream getExampleCode() {
//        String example = 									
//        		"package mathtest;\n"+
//                "public class Calculator { \n"
//               + "  public void testAdd() { "
//               + "    System.out.println(200+300); \n"
//               + "    org.apache.commons.io.IOUtils aaaa; \n"
//             + "   } \n"
//               + "  public static void main(String[] args) { \n"
//               + "    Calculator cal = new Calculator(); \n"
//               + "    cal.testAdd(); \n"
//               + "  } " + "} ";	
//        return new ByteArrayInputStream(example.getBytes());
//	}
	
    private  JavaFileObject getJavaSourceFileObject(String className, InputStream contents) throws IOException
    {
        JavaFileObject so = null;
            String replace = className.replace('.', '/');
            replace = replace.replaceAll(" ", "%20");
			so = new CustomJavaFileObject(className+ Kind.SOURCE.extension, URI.create("file:///" + replace
                    + Kind.SOURCE.extension), contents, Kind.SOURCE);
        return so;
    }
}
