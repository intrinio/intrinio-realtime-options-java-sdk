package intrinio;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import com.google.gson.Gson;

public class Config {
	
	private static final String filename = "intrinio/config.json";
	
	private String apiKey;
	private Provider provider = Provider.NONE;
	private String ipAddress;
	private String[] symbols;
	private int numThreads = 4;
	
	public Config(String apiKey, Provider provider, String ipAddress, String[] symbols, int numThreads) throws Exception {
		this.apiKey = apiKey;
		this.provider = provider;
		this.ipAddress = ipAddress;
		this.symbols = symbols;
		this.numThreads = numThreads;
		
		if (this.apiKey.isBlank()) {
			throw new Exception("You must provide a valid API key");
		}
		if (this.provider == Provider.NONE) {
			throw new Exception("You must specify a valid provider");
		}
		if ((this.provider == Provider.MANUAL) && this.ipAddress.isBlank()) {
			throw new Exception("You must specify an IP address for manual configuration");
		}
	}
	
	public String getApiKey() {
		return apiKey;
	}

	public Provider getProvider() {
		return provider;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String[] getSymbols() {
		return symbols;
	}

	public int getNumThreads() {
		return numThreads;
	}
	
	public String toString() {
		return String.format("apiKey = %s, provider = %s, ipAddress = %s, symbols = %s, numThreads = %d",
				this.apiKey,
				this.provider,
				this.ipAddress,
				(this.symbols == null ? "[]" : "[ " + String.join(", ", this.symbols) + " ]"),
				this.numThreads);
	}

	public static Config load() {
		System.out.println("Loading application configuration");
		try {
			InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(Config.filename);
			Reader reader = new InputStreamReader(inputStream);
			Gson gson = new Gson();
			Config config = gson.fromJson(reader, Config.class);
			System.out.println(config);
			return config;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}