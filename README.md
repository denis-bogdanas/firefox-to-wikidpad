# Instructions

1. Install Java 8 and a git client.
2. Clone this repository.
3. Compile the sources. From the root directory run:
```
javac -cp src src/Main.java
```
4. Open Wikidpad
5. Create a fresh wiki. Choose original sqlite as database backend.
6. Find firefox profile directory: https://support.mozilla.org/en-US/kb/profiles-where-firefox-stores-user-data
7. Run bookmarks importer. The importer requires 8 arguments, always in he same order:
```
java -cp "src;lib/*" Main
-in <path to places.sqlite inside firefox profile dir>
-out <path to newly created wiki root dir>
-compactBookmarks <true/false> 
-pageSizeThreshold <number>
```

- compactBookmarks meaning: 
  - true = bookmarks a la [name|link]
  - false = boolmarks a la name: link
- pageSizeThreshold meaning: The maximal number of elements (links and directories) a page may contain. if a page has less than pageSizeThreshold elements, all its elements will be recursively included into this page. Set to 1 to have one page for each directory. Set to a large value, like 10000, to generate the whole wiki in one page.

8. Go back to wikidpad. Notice the main page has changed.
9. Main menu -> Wiki -> Maintenance -> Rebuild wiki. Now the main menu should contain all the pages.
10. Optional: disable CamelCase wiki words. If you don't, Wikidpad will create a lot of phantom pages, for words like YouTube.
      In WikiSettings page add the line:      
```      
  [global.camelCaseWordsEnabled: false]
```
Run rebuild wiki again.
            

You are good to go. Feel free to experiment with different values of compactBookmarks and pageSizeThreshold, to find the value that best suits your need. My settings were false and 100.
