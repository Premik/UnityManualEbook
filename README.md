# Unity manual to eBook

![UnityManualInKindle](doc/UnityManualInKindle.jpg)

## Steps

### Download the manual

Download the offline documentation from the unity3d page. For example: [2018.2](https://docs.unity3d.com/2018.2/Documentation/Manual/OfflineDocumentation.html).

```bash
 wget https://docs.unity3d.com/2018.1/Documentation/uploads/UnityDocumentation.zip
 unzip UnityDocumentation.zip -d /tmp/unity/
 cd /tmp/unity/Documentation/en
```

### Convert images

**This is an optional step**. Not needed if you are using a device with full capable web browser.

There are `jpg`, `png`, `gif`, `svg` and `pdf` files included in the manual. I wanted all the images to be grayscaled png files for my Kindle reader.

Convert `svg` with the [Inkscape](https://inkscape.org):

```bash
find . -name "*.svg" -exec inkscape -z -e {}.png {} \;
 ```

Convert `jpg` with [ImageMagic's](https://www.imagemagick.org/script/index.php) `convert`:

```bash
find . -name "*.jpg" -exec convert -verbose {} {}.png \;
```

The same for the `gif`s. Since the gifs are animated the `convert` will create multiple numbered `png` 
files for each frame. The groovy script can deal with this but it takes only one frame from the animation.

```bash
find . -name "*.gif"  -exec convert -verbose {} {}.png \;
```

Convert all the pngs to grayscale with the [pngcrush](https://pmt.sourceforge.io/pngcrush/).

```bash
find . -name "*.png" -exec pngcrush -brute -l 9 -rem gAMA -rem cHRM -rem iCCP -rem sRGB -rem alla -rem text -c 0 {} {}.png \;
```

As an alternative the `mogrify` from the ImageMagic can be used too. But it would produce bigger images.
The the `pngcrush` is slow. It takes more than 30 minutes to convert all the pngs on an average pc. But the resulted images (after bit-depth reduction and grayscale) are `3x-5x` times smaller.

```bash
#find . -name "*.png" -exec mogrify -verbose -type Grayscale -depth 8 -quality 9 -format png {}  \;
```

Note all the above commands (except the last one ) will produce a copy of the file with multiple extensions.
Like `foobar.svg.png.png`. The groovy script updates all the image links while prefering the longest file name.
So the `foobar.svg.png.png` will be used instead of the original `foobar.svg`

![Pngs](doc/Pngs.png)

### The Groovy Script

The `ManualProcessor.groovy` script:

- Parses the TOC file to recognize order of the pages .
- Linearizes and removes unnecessary content from each page.
- Concacenate multiple pages into single html file.
- For each first level chapter creates a new html file.
- Makes sure there are not too many pages in single chapters file.
- Add number prefix for each file so the order is obvious.
- Unsure the img links are correct after above image conversation.

#### Steps

- Get the script. Clone this repo or just: 

```bash
wget https://raw.githubusercontent.com/Premik/UnityManualEbook/master/ManualProcessor.groovy 
```

- Get and install [Groovy](http://groovy-lang.org/)
- Review the grovy script. Currently `maxSubChaptersInOneFile` is set to `12`.
- Run the script inside the unziped folder `Documentation/en/`:

```bash
groovy UnityManual.groovy
```
It will produce new **Manual-flattened** with the linearized html files:

 * `Manual`
 * **Manual-flattened**
   * `01-UnityManual-partA-UnityManual.html`
   * `02-UnityOverview-partA-UnityOverview.html`
   * `02-UnityOverview-partB-AssetStore.html`
   * `02-UnityOverview-partC-PresetLibraries.html`
   * `03-ImportingAssets-partA-ImportingAssets.html`
   * `...`
 * `ScriptReference`
 * `StaticFiles`
 * `StaticFilesManual`
 * `upload`