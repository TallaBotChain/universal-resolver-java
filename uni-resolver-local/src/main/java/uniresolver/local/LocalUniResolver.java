package uniresolver.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import did.DID;
import did.parser.ParserException;
import uniresolver.ResolutionException;
import uniresolver.UniResolver;
import uniresolver.driver.Driver;
import uniresolver.result.ResolveResult;

public class LocalUniResolver implements UniResolver {

	private static Logger log = LoggerFactory.getLogger(LocalUniResolver.class);

	private static final LocalUniResolver DEFAULT_RESOLVER;

	private Map<String, Driver> drivers = new HashMap<String, Driver> ();

	static {

		DEFAULT_RESOLVER = new LocalUniResolver();
	}

	public LocalUniResolver() {

	}

	public static LocalUniResolver getDefault() {

		return DEFAULT_RESOLVER;
	}

	@Override
	public ResolveResult resolve(String identifier) throws ResolutionException {

		return this.resolve(identifier, null);
	}

	@Override
	public ResolveResult resolve(String identifier, String selectServiceType) throws ResolutionException {

		if (identifier == null) throw new NullPointerException();

		if (this.getDrivers() == null) throw new ResolutionException("No drivers configured.");

		// start time

		long start = System.currentTimeMillis();

		// try all drivers

		ResolveResult ResolveResult = null;
		String usedDriverId = null;
		Driver usedDriver = null;

		for (Entry<String, Driver> driver : this.getDrivers().entrySet()) {

			if (log.isDebugEnabled()) log.debug("Attemping to resolve " + identifier + " with driver " + driver.getValue().getClass());
			ResolveResult = driver.getValue().resolve(identifier);

			if (ResolveResult != null) {

				usedDriverId = driver.getKey();
				usedDriver = driver.getValue();
				break;
			}
		}

		if (log.isDebugEnabled()) log.debug("Resolved " + identifier + " with driver " + usedDriverId);

		// result contains a new did?

		List<String> initialIdentifiers = new ArrayList<String> ();
		List<ResolveResult> initialResolveResults = new ArrayList<ResolveResult> ();

		while (ResolveResult != null && ResolveResult.getMethodMetadata().containsKey("did")) {

			initialIdentifiers.add(identifier);
			initialResolveResults.add(ResolveResult);

			identifier = (String) ResolveResult.getMethodMetadata().get("did");

			for (Entry<String, Driver> driver : this.getDrivers().entrySet()) {

				if (log.isDebugEnabled()) log.debug("Attemping to resolve " + identifier + " with driver " + driver.getValue().getClass());
				ResolveResult = driver.getValue().resolve(identifier);

				if (ResolveResult != null) {

					usedDriverId = driver.getKey();
					usedDriver = driver.getValue();
					break;
				}
			}

			if (log.isDebugEnabled()) log.debug("Resolved " + identifier + " with driver " + usedDriverId);
		}

		if (initialIdentifiers.isEmpty()) initialIdentifiers = null;
		if (initialResolveResults.isEmpty()) initialResolveResults = null;

		// stop time

		long stop = System.currentTimeMillis();

		// no driver was able to fulfill a request?

		if (ResolveResult == null && initialResolveResults != null && initialIdentifiers != null) {

			identifier = initialIdentifiers.get(0);
			ResolveResult = initialResolveResults.get(0);
			if (log.isDebugEnabled()) log.debug("Falling back to initial identifier and resolve result: " + identifier);
		}

		if (ResolveResult == null) {

			if (log.isDebugEnabled()) log.debug("No resolve result.");
			return null;
		}

		// parse DID

		DID didReference = null;

		try {

			didReference = DID.fromString(identifier);
			log.debug("identifier " + identifier + " is a valid DID reference: " + didReference.getDid());

			identifier = didReference.getDid();
		} catch (IllegalArgumentException | ParserException ex) {

			log.debug("Identifier " + identifier + " is not a valid DID reference: " + ex.getMessage());
		}

		// service selection

		String selectServiceName = didReference != null ? didReference.getService() : null;
		Integer[] selectedServices;

		if (selectServiceName == null && selectServiceType == null) {

			selectedServices = null;
		} else {

			selectedServices = ResolveResult.getDidDocument().selectServices(selectServiceName, selectServiceType);
		}

		// add RESOLVER METADATA

		Map<String, Object> resolverMetadata = new LinkedHashMap<String, Object> ();
		resolverMetadata.put("driverId", usedDriverId);
		resolverMetadata.put("driver", usedDriver.getClass().getSimpleName());
		resolverMetadata.put("duration", Long.valueOf(stop - start));
		if (initialIdentifiers != null) resolverMetadata.put("initialIdentifiers", initialIdentifiers);
		if (didReference != null) resolverMetadata.put("didReference", didReference);
		if (selectedServices != null) resolverMetadata.put("selectedServices", selectedServices);

		ResolveResult.setResolverMetadata(resolverMetadata);

		// done

		return ResolveResult;
	}

	@Override
	public Map<String, Map<String, Object>> properties() throws ResolutionException {

		if (this.getDrivers() == null) throw new ResolutionException("No drivers configured.");

		Map<String, Map<String, Object>> properties = new HashMap<String, Map<String, Object>> ();

		for (Entry<String, Driver> driver : this.getDrivers().entrySet()) {

			if (log.isDebugEnabled()) log.debug("Loading properties for driver " + driver.getKey() + " (" + driver.getValue().getClass().getSimpleName() + ")");

			Map<String, Object> driverProperties = driver.getValue().properties();
			if (driverProperties == null) driverProperties = Collections.emptyMap();

			properties.put(driver.getKey(), driverProperties);
		}

		if (log.isDebugEnabled()) log.debug("Loading properties: " + properties);

		return properties;
	}

	/*
	 * Getters and setters
	 */

	public Map<String, Driver> getDrivers() {

		return this.drivers;
	}

	@SuppressWarnings("unchecked")
	public <T extends Driver> T getDriver(Class<T> driverClass) {

		for (Driver driver : this.getDrivers().values()) {

			if (driverClass.isAssignableFrom(driver.getClass())) return (T) driver;
		}

		return null;
	}

	public void setDrivers(Map<String, Driver> drivers) {

		this.drivers = drivers;
	}
}
