# SimpleWebCrawl
A simple little web crawler application.

This program works by taking an initial starting URL, which it places in a queue. It continuously takes a URL off the front of the queue, finds all of the links in the .html file located at that address, then adds all of the links to the queue. It will keep running until it hits a maximum iteration count, since the queue will never be empty.

While running, it will save local copies of .html files in a folder. The local files have some metadata on the first part, then the actual file seperated by a `%%` line.

![Screenshot of the UI](https://raw.githubusercontent.com/kvn-prhn/SimpleWebCrawl/master/MyWebCrawlScreenshot.png)

The logging on the UI is not very good, but a lot of information goes to the standard output. 
