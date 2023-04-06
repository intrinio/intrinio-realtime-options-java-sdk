# Intrinio Java SDK for Real-Time Option Prices
SDK for working with Intrinio's realtime options feed via WebSocket

[Intrinio](https://intrinio.com/) provides real-time stock option prices via a two-way WebSocket connection. To get started, [subscribe to a real-time data feed](https://intrinio.com/financial-market-data/options-data) and follow the instructions below.

## Requirements

- Java 14+

## Docker
Add your API key to the config.json file in src/intrinio, then
```
docker compose build
docker compose run example
```

## Installation

Go to [Release](https://github.com/intrinio/intrinio-realtime-options-java-sdk/releases/), download the JAR, reference it in your project. The JAR contains dependencies necessary to the SDK.

## Sample Project

For a sample Java project see: [intrinio-realtime-options-java-sdk](https://github.com/intrinio/intrinio-realtime-options-java-sdk)

## Features

* Receive streaming, real-time option price updates:
	* every trade
	* conflated bid and ask
	* open interest, open, close, high, low
	* unusual activity(block trades, sweeps, whale trades, unusual sweeps)
* Subscribe to updates from individual options contracts (or option chains)
* Subscribe to updates for the entire universe of option contracts (~1.5M option contracts)

## Example Usage

```java
package SampleApp;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import intrinio.*;

class TradeHandler implements OnTrade {

    public AtomicInteger tradeCount = new AtomicInteger(0);

    public void onTrade(Trade trade) {
        tradeCount.incrementAndGet();
    }
}

class QuoteHandler implements OnQuote {
    public AtomicInteger quoteCount = new AtomicInteger(0);

    public void onQuote(Quote quote) {
        quoteCount.incrementAndGet();
    }
}

class RefreshHandler implements OnRefresh {
    public AtomicInteger rCount = new AtomicInteger(0);

    public void onRefresh(Refresh r) {
        rCount.incrementAndGet();
    }
}

class UnusualActivityHandler implements OnUnusualActivity {
    public AtomicInteger blockCount = new AtomicInteger(0);
    public AtomicInteger sweepCount = new AtomicInteger(0);
    public AtomicInteger largeTradeCount = new AtomicInteger(0);
    public AtomicInteger unusualSweepCount = new AtomicInteger(0);

    public void onUnusualActivity(UnusualActivity ua) {
        switch (ua.type()){
            case BLOCK:
                blockCount.incrementAndGet();
                break;
            case SWEEP:
                sweepCount.incrementAndGet();
                break;
            case LARGE:
                largeTradeCount.incrementAndGet();
                break;
            case UNUSUAL_SWEEP:
                unusualSweepCount.incrementAndGet();
                break;
            default:
                Client.Log("Sample App - Invalid UA type detected: %s", ua.type().toString());
                break;
        }
    }
}

public class SampleApp {

    public static void main(String[] args) {
        Client.Log("Starting sample app");

        // Create only the handlers/callbacks that you need
        // These will get registered below
        TradeHandler tradeHandler = new TradeHandler();
        QuoteHandler quoteHandler = new QuoteHandler();
        RefreshHandler refreshHandler = new RefreshHandler();
        UnusualActivityHandler unusualActivityHandler = new UnusualActivityHandler();

        // You can either create a config class or default to using the intrinio/config.json file
        //Config config = null;
        //try {config = new Config("apiKeyHere", Provider.OPRA, null, null, 8);} catch (Exception e) {e.printStackTrace();}
        //Client client = new Client(config);
        Client client = new Client();

        // Register a callback for a graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
            public void run() {
                Client.Log("Stopping sample app");
                client.stop();
            }
        }));

        try {
            // Register only the callbacks that you want.
            // Take special care when registering the 'OnQuote' handler as it will increase throughput by ~10x
            client.setOnTrade(tradeHandler);
            //client.setOnQuote(quoteHandler);
            client.setOnRefresh(refreshHandler);
            client.setOnUnusualActivity(unusualActivityHandler);

            // Start the client
            client.start();

            // Use this to subscribe to a static list of symbols (option contracts) provided in config.json
            //client.join();

            // Use this to subscribe to the entire univers of symbols (option contracts). This requires special permission.
            //client.joinLobby();

            // Use this to subscribe, dynamically, to an option chain (all option contracts for a given underlying symbol).
            //client.join("AAPL");

            // Use this to subscribe, dynamically, to a specific option contract.
            //client.join("AAP___230616P00250000");

            // Use this to subscribe, dynamically, a list of specific option contracts or option chains.
            //client.join(new String[] {"GOOG__210917C01040000", "MSFT__210917C00180000", "AAPL__210917C00130000", "TSLA"});

        } catch (Exception e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                Client.Log(client.getStats());
                String appStats = String.format(
                        "Messages (Trades = %d, Quotes = %d, Refreshes = %d, Blocks = %d, Sweeps = %d, Larges = %d, UnusualSweeps = %d)",
                        tradeHandler.tradeCount.get(),
                        quoteHandler.quoteCount.get(),
                        refreshHandler.rCount.get(),
                        unusualActivityHandler.blockCount.get(),
                        unusualActivityHandler.sweepCount.get(),
                        unusualActivityHandler.largeTradeCount.get(),
                        unusualActivityHandler.unusualSweepCount.get());
                Client.Log(appStats);
            }
        };
        timer.schedule(task, 10000, 10000);
    }
}
```

## Handling Quotes

There are millions of options contracts, each with their own feed of activity.
We highly encourage you to make your OnTrade, OnQuote, OnUnusualActivity, and OnRefresh methods as short as possible and follow a queue pattern so your app can handle the large volume of activity.
Note that quotes (ask and bid updates) comprise 99% of the volume of the entire feed. Be cautious when deciding to receive quote updates.

## Providers

Currently, Intrinio offers realtime data for this SDK from the following providers:

* OPRA - [Homepage](https://www.opraplan.com/)


## Data Format

### Trade Message

```java
public record Trade(String contract, double price, long size, double timestamp, long totalVolume, double askPriceAtExecution, double bidPriceAtExecution, double underlyingPriceAtExecution)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **price** - the price in USD
* **size** - the size of the last trade in hundreds (each contract is for 100 shares).
* **totalVolume** - The number of contracts traded so far today.
* **timestamp** - a Unix timestamp (with microsecond precision)
* **askPriceAtExecution** - the last best ask price in USD at execution.
* **bidPriceAtExecution** - the last best bid price in USD at execution.
* **underlyingPriceAtExecution** - the price of the underlying security at execution.


### Quote Message

```java
public record Quote(String contract, double askPrice, long askSize, double bidPrice, long bidSize, double timestamp)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **askPrice** - the last best ask price in USD
* **askSize** - the last best ask size of the last ask or bid in hundreds (each contract is for 100 shares).
* **bidPrice** - the last best bid price in USD
* **bidSize** - the last best bid size of the last ask or bid in hundreds (each contract is for 100 shares).
* **timestamp** - a Unix timestamp (with microsecond precision)


### Open Interest Message

```java
public record Refresh (String contract, long openInterest, double openPrice, double closePrice, double highPrice, double lowPrice)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **openInterest** - the total quantity of opened contracts as reported at the start of the trading day
* **openPrice** - the opening price for the day
* **closePrice** - the closing price for the day
* **highPrice** - the current high price for the day 
* **lowPrice** - the current low price for the day

### Unusual Activity Message
```java
public record UnusualActivity(
        String contract,
        UnusualActivityType type,
        UnusualActivitySentiment sentiment,
        double totalValue,
        long totalSize,
        double averagePrice,
        double askPriceAtExecution,
        double bidPriceAtExecution,
        double underlyingPriceAtExecution,
        double timestamp)
```

* **contract** - Identifier for the options contract.  This includes the ticker symbol, put/call, expiry, and strike price.
* **type** - The type of unusual activity that was detected
  * **`Block`** - represents an 'block' trade
  * **`Sweep`** - represents an intermarket sweep
  * **`Large`** - represents a trade of at least $100,000
  * **`Unusual Sweep`** - represents an unusually large sweep near market open
* **sentiment** - The sentiment of the unusual activity event
  *    **`Neutral`** - 
  *    **`Bullish`** - 
  *    **`Bearish`** - 
* **totalValue** - The total value of the trade in USD. 'Sweeps' and 'blocks' can be comprised of multiple trades. This is the value of the entire event.
* **totalSize** - The total size of the trade in number of contracts. 'Sweeps' and 'blocks' can be comprised of multiple trades. This is the total number of contracts exchanged during the event.
* **averagePrice** - The average price at which the trade was executed. 'Sweeps' and 'blocks' can be comprised of multiple trades. This is the average trade price for the entire event.
* **askAtExecution** - The 'ask' price of the contract at execution of the trade event.
* **bidAtExecution** - The 'bid' price of the contract at execution of the trade event.
* **underlyingPriceAtExecution** - The last trade price of the underlying security at execution of the trade event.
* **timestamp** - a Unix timestamp (with microsecond precision).

## API Keys

You will receive your Intrinio API Key after [creating an account](https://intrinio.com/signup). You will need a subscription to a [realtime data feed](https://intrinio.com/financial-market-data/options-data) as well.

## Documentation

### Overview

The Intrinio Realtime Client will handle authorization as well as establishment and management of all necessary WebSocket connections. All you need to get started is your API key.
The first thing that you'll do is create a new `Client` object.
After a `Client` object has been created, you will immediately register a series of callbacks, using the `setOnX` methods (e.g. `setOnTrade`). These callback methods tell the client what types of subscriptions you will be setting up.
You must register callbacks in order to receive data. And you will only receive data for the types of callbacks that you have registered (i.e. you will only receive trade updates if you register an `OnTrade` callback).
After registering your desired callbacks, you may subscribe to receive feed updates from the server.
You may subscribe to a static list of symbols (a mixed list of option contracts and/or option chains). 
Or, you may subscribe, dynamically, to option contracts, option chains, or a mixed list thereof.
It is also possible to subscribe to the entire universe of option contracts by switching the `Provider` to "OPRA_FIREHOSE" (in config.json) and calling `JoinLobby`.
The volume of data provided by the `Firehose` exceeds 100Mbps and requires special authorization.
After subscribing you starting list of symbols, you will call the `start` method. The client will immediately attempt to authorize your API key (provided in the config.json file). If authoriztion is successful, the necessary connection(s) will be opened.
If you are using the non-firehose feed, you may update your subscriptions on the fly, using the `join` and `leave` methods.
The WebSocket client is designed for near-indefinite operation. It will automatically reconnect if a connection drops/fails and when then servers turn on every morning.
If you wish to perform a graceful shutdown of the application, please call the `stop` method.

### Methods

`Client client = new Client(Config config)` - Creates an Intrinio Real-Time client.
* **Parameter** `config`: Optional - The configuration to be used by the client. If this value is not provided, `config.json` will be picked up (from the project root) and used.

---------

`client.setOnTrade(OnTrade onTrade) throws Exception` - Registers a callback that is invoked for every trade update. If no `onTrade` callback is registered with this method, you will not receive trade updates from the server.
* **Parameter** `onTrade`: The handler for trade events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnTrade` has already been set.

`client.setOnQuote(OnQuote onQuote) throws Exception` - Registers a callback that is invoked for every quote update. If no `onQuote` callback is registered with this method, you will not receive quote (ask, bid) updates from the server.
* **Parameter** `onQuote`: The handler for quote events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnQuote` has already been set.

`client.setOnRefresh(OnRefresh onRefresh) throws Exception` - Registers a callback that is invoked for refresh update. If no `onRefresh` callback is registered with this method, you will not receive open interest, high, low, open, close data from the server.
* **Parameter** `onRefresh`: The handler for refresh events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnRefresh` has already been set.

`client.setOnUnusualActivity(OnUnusualActivity onUnusualActivity) throws Exception` - Registers a callback that is invoked for every unusual trade. If no `onUnusualActivity` callback is registered with this method, you will not receive unusual trade updates from the server.
* **Parameter** `onUnusualActivity`: The handler for unusual trade events.
* **Throws** `Exception`: If the start method has already been called. Or if `OnUnusualActivity` has already been set.

---------

`client.start()` - Starts the Intrinio Realtime WebSocket Client.
	This method will immediately attempt to authorize the API key (provided in config).
	After successful authorization, all of the data processing threads will be started, and the websocket connections will be opened.
	If a subscription has already been created with one of the `join` methods, data will begin to flow.

---------

`client.join()` - Joins channel(s) configured in config.json.
`client.join(String channel)` - Joins the provided channel. E.g. "AAPL" or "GOOG__210917C01040000"
`client.join(String[] channels)` - Joins the provided channels. E.g. [ "AAPL", "MSFT__210917C00180000", "GOOG__210917C01040000" ]
`client.joinLobby()` - Joins the 'lobby' (aka. firehose) channel. The provider must be set to `OPRA_FIREHOSE` for this to work. This requires special account permissions.

---------

`client.leave()` - Leaves all joined channels/subscriptions, including `lobby`.
`client.leave(String channel)` - Leaves the specified channel. E.g. "AAPL" or "GOOG__210917C01040000"
`client.leave(String[] channels)` - Leaves the specified channels. E.g. [ "AAPL", "MSFT__210917C00180000", "GOOG__210917C01040000" ]
`client.leaveLobby()` Leaves the `lobby` channel 

---------
`client.stop();` - Stops the Intrinio Realtime WebSocket Client. This method will leave all joined channels, stop all threads, and gracefully close the websocket connection(s).


## Configuration

### config.json
```json
{
	"apiKey": "",
	"provider": "OPRA", //OPRA, or MANUAL
	"symbols": [ "GOOG__210917C01040000", "MSFT__210917C00180000", "AAPL__210917C00130000", "SPY" ], //Individual contracts (or option chains) to subscribe to all.
	"numThreads": 4 //The number of threads to use for processing events.
}
```

