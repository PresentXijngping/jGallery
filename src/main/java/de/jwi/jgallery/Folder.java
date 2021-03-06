
package de.jwi.jgallery;

/*
 * jGallery - Java web application to display image galleries
 * 
 * Copyright (C) 2004 Juergen Weber
 * 
 * This file is part of jGallery.
 * 
 * jGallery is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * jGallery is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with jGallery; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston
 */

import imageinfo.ImageInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletContext;

import de.jwi.jgallery.db.DBManager;

/**
 * @author Juergen Weber Source file created on 17.02.2004
 * 
 */
public class Folder implements Serializable
{

	private static final String FOLDER_KEY = "folder";

	private static final String CAPTIONSFILE = "captions.txt";

	private ConfigData configData;

	private static final String GENERATORURL = "http://www.jwi.de/jGallery/";

	private String thumbsdir = "thumbs";

	private float thumbQuality = 0.98f;

	private int thumbSize = 150;

	private boolean isDirectoryParsed = false;

	private boolean isProcessSubdirectories = true;

	private boolean isCreateThumbs = true;

	private IThumbnailWriter thumbnailWriter = null;

	private File directory;

	private List parentFolderList;

	private Configuration configuration;

	protected String[] imageFiles;

	private Properties captions = new Properties();

	private int[] imageCounters;

	private int folderCounter = -1;

	String[] subDirectories;

	private String iconWidth;

	private String iconHeight;

	private final int FOLDER_ICON_RANDOM = 0;

	private final int FOLDER_ICON_ICON = 1;

	private int folderIconStyle = FOLDER_ICON_RANDOM;

	protected String folderPath;

	protected String imagePath;

	protected String jgalleryContextPath;

	private ParentLink parentIndexPage;

	private String parentlink;

	private Hashtable images = new Hashtable();

	private Image[] imagesArray;

	private Image image; // the current image

	private int imageNum; // Number of the current image

	private String template = "Standard"; // Name (and folder) of template

	private String textEncoding = "iso-8859-1";

	private String indexJsp;

	private String slideJsp;

	private String templatePath;

	private String resPath;

	private String resResourcePath;

	private String stylePath;

	private String style = "Black";

	private int level;

	private int cols = 2; // Number of image columns on index pages

	private int currentCols;

	private int currentRows;

	private int rows = 3; // Max number of image rows on index pages

	private boolean isShowImageNum = true;

	private boolean isShowDates = true;

	private int totalIndexes; // Total number of index pages

	private int totalAlbumImages; // Total number of images in an album

	// (subdirectory images included)
	private int totalImages; // Total number of images in a directory

	private int indexNum; // The number of the current index page

	private String albumPath;

	private String title;

	private String name;

	private HashMap variables = new HashMap();

	private DBManager dBManager;

	private ServletContext appContext;

	private boolean firstIndexPageWasDisplayed = false;

	public Folder(File directory, ServletContext appContext, Configuration configuration, ConfigData configData,
			String jgalleryContextPath, String folderPath, String imagePath, DBManager dBManager)
			throws GalleryException
	{
		this.directory = directory;

		this.appContext = appContext;
		this.configData = configData;

		this.configuration = configuration;

		this.jgalleryContextPath = jgalleryContextPath;
		this.folderPath = folderPath;
		this.imagePath = imagePath;

		this.dBManager = dBManager;

		readConfiguration();

		thumbnailWriter = configuration.getThumbnailWriter();

	}

	public HashMap getVariables()
	{
		return variables;
	}

	private int sortCriterion = ImageComparator.SORTNONE;

	private int sortOrder = ImageComparator.SORT_ASC;

	private Configuration readTemplateConfiguration(String templateConfigFile, Configuration c)
	{
		Configuration c1 = c;

		InputStream is = appContext.getResourceAsStream(templateConfigFile);

		if (is != null)
		{
			try
			{
				c1 = new Configuration(is, c);
			} catch (IOException e)
			{
				// NOP
			} finally
			{
				try
				{
					is.close();
				} catch (IOException e1)
				{
					// NOP
				}
			}
		}
		return c1;
	}

	private void readConfiguration() throws GalleryException
	{
		template = configuration.getString("template", template);
		String templateConfig = "/templates/" + template + "/template.properties";

		// Template Configurations cannot be overridden
		configuration = readTemplateConfiguration(templateConfig, configuration);

		cols = configuration.getInt("index.columns", cols);
		rows = configuration.getInt("index.rows", rows);

		indexJsp = "/templates/" + template + "/index.jsp";
		slideJsp = "/templates/" + template + "/slide.jsp";

		style = configuration.getString("style", style);

		textEncoding = configuration.getString("textEncoding", textEncoding);

		String s = configuration.getString("sortCriterion");
		if ("filedate".equals(s))
		{
			sortCriterion = ImageComparator.SORTBYFILEDATE;
		} else if ("exifdate".equals(s))
		{
			sortCriterion = ImageComparator.SORTBYEXIFDATE;
		} else if ("name".equals(s))
		{
			sortCriterion = ImageComparator.SORTBYNAME;
		} else if ("none".equals(s))
		{
			sortCriterion = ImageComparator.SORTNONE;
		} else
		{
			sortCriterion = ImageComparator.SORTNONE;
		}

		s = configuration.getString("sortOrder");
		if ("asc".equals(s))
		{
			sortOrder = ImageComparator.SORT_ASC;
		} else if ("desc".equals(s))
		{
			sortOrder = ImageComparator.SORT_DESC;
		} else
		{
			sortOrder = ImageComparator.SORT_ASC;
		}

		isShowImageNum = configuration.getBoolean("showImageNum", isShowImageNum);

		folderIconStyle = FOLDER_ICON_RANDOM;

		s = configuration.getString("foldericon.style");
		if ("icon".equalsIgnoreCase(s))
		{
			folderIconStyle = FOLDER_ICON_ICON;
		}

		thumbsdir = configuration.getString("thumbnails.dir", thumbsdir);

		isCreateThumbs = configuration.getBoolean("thumbnails.create", isCreateThumbs);

		thumbSize = configuration.getInt("thumbnails.size", thumbSize);
		thumbQuality = configuration.getFloat("thumbnails.quality", thumbQuality);

		templatePath = jgalleryContextPath + "/templates/" + template;
		resResourcePath = "/templates/" + template + "/res";
		resPath = jgalleryContextPath + "/templates/" + template + "/res";
		stylePath = jgalleryContextPath + "/templates/" + template + "/styles/" + style + ".css";

		s = configuration.getString("parentlink");
		if (s == null)
		{
			String s1 = folderPath.substring(1, folderPath.indexOf('/', 1));
			s = configuration.getString("parentlink." + s1);
		}

		if (s != null)
		{
			parentlink = s;
		}

		setIconDimensions();

		configuration.getUserVariables(variables);
	}

	private void setIconDimensions()
	{
		InputStream is = appContext.getResourceAsStream(resResourcePath + "/folder.gif");
		if (null != is)
		{

			ImageInfo ii = new ImageInfo();
			ii.setInput(is);
			if (ii.check())
			{
				iconWidth = Integer.toString(ii.getWidth());
				iconHeight = Integer.toString(ii.getHeight());
			}
		}
	}

	File getDirectory()
	{
		return directory;
	}

	public String getUrlExtention()
	{
		return configData.urlExtention;
	}

	public String getShowDates()
	{
		return Boolean.toString(isShowDates);
	}

	public String getShowImageNum()
	{
		return Boolean.toString(isShowImageNum);
	}

	public String getComment()
	{
		String s = captions.getProperty(FOLDER_KEY);

		if (s != null)
		{
			return s;
		}

		s = configuration.getString(FOLDER_KEY);
		return (s == null) ? "" : s;
	}

	public String getComment(String image)
	{
		String s = captions.getProperty(image);

		return (s == null) ? "" : s;
	}

	public String getIndexJsp()
	{
		return indexJsp;
	}

	public String getSlideJsp()
	{
		return slideJsp;
	}

	public Image getImage()
	{
		return image;
	}

	protected IImageAccessor makeImageAccessor(String name)
	{
		return new FileImageAccessor(name, this);
	}

	private ThumbNailInfo makeThumbNailInfoFromRandom(String subDirectoryName) throws GalleryException
	{
		File f = null;
		String subImages[] = null;

		f = new File(getDirectory(), subDirectoryName + "/" + getThumbsdir());
		subImages = f.list(new FilenameFilter()
		{
			public boolean accept(File dir, String name)
			{
				File f1 = new File(dir, name);
				if (!isJPEGExtension(name))
				{
					return false;
				}
				return !f1.isDirectory();
			};
		});

		if (subImages == null)
		{
			throw new GalleryException("makeThumbNailInfoFromRandom called for non-folder " + subDirectoryName);
		}

		// if (subImages == null)
		// {
		// Folder subFolder = new Folder(new File(directory, subDirectoryName),
		// appContext, configuration, configData, jgalleryContextPath,
		// folderPath + "/" + subDirectoryName, imagePath + "/" +
		// subDirectoryName, dBManager);
		//
		// subFolder.loadFolder();
		//
		// // no thumbnails created yet
		//
		// }
		int n = (int) (Math.random() * subImages.length);

		File f1 = new File(f, subImages[n]);

		InputStream is = null;
		try
		{
			is = new FileInputStream(f1);
		} catch (FileNotFoundException e)
		{
			// guaranteed that file exists
		}
		ImageInfo ii = new ImageInfo();

		ii.setInput(is);

		if (!ii.check())
		{
			throw new GalleryException("Not a supported image file format.");
		}

		String thumbWidth = Integer.toString(ii.getWidth());
		String thumbHeight = Integer.toString(ii.getHeight());

		try
		{
			is.close();
		} catch (IOException e1)
		{
			// NOP
		}

		ThumbNailInfo info = new ThumbNailInfo(
				getImageBasePath() + subDirectoryName + "/" + getThumbsdir() + "/" + subImages[n], thumbWidth,
				thumbHeight);

		return info;

	}

	public Image getSubDirOrImage(int n) throws GalleryException
	{
		Image image;
		if (isProcessSubdirectories)
		{
			if (n > subDirectories.length)
			{
				// get an image
				image = getImage(n);
			} else
			{
				ThumbNailInfo thumbNailInfo = null;

				if (folderIconStyle == FOLDER_ICON_ICON)
				{
					thumbNailInfo = new ThumbNailInfo(getIconPath(), getIconHeight(), getIconWidth());
				} else
				{
					thumbNailInfo = makeThumbNailInfoFromRandom(subDirectories[n - 1]);
					if (thumbNailInfo == null)
					{
						// no thumbnails created yet, use icon for now
						thumbNailInfo = new ThumbNailInfo(getIconPath(), getIconHeight(), getIconWidth());
					}
				}
				// get a subfolder representation
				// the the Image constructor closes all used file handles
				image = new Image(subDirectories[n - 1], true, this, makeImageAccessor(subDirectories[n - 1]),
						thumbNailInfo);

				imagesArray[n - 1] = image;
			}
		} else
		{
			image = getImage(n);
		}
		return image;
	}

	// lazy load Image n (1..)
	public Image getImage(int n) throws GalleryException
	{
		if (null == imagesArray[n - 1])
		{
			// create the thumb first, as it is needed in the Image constructor

			if (isJPEGExtension(imageFiles[n - 1]))
			{
				checkAndCreateThumb(n - 1);
			}
			imagesArray[n - 1] = new Image(imageFiles[n - 1], false, this, makeImageAccessor(imageFiles[n - 1]), null);

			String s = captions.getProperty(imageFiles[n - 1]);
			if (s != null)
			{
				imagesArray[n - 1].setComment(s);
			}
		}
		return imagesArray[n - 1];
	}

	// n = 0..
	private void checkAndCreateThumb(int n) throws GalleryException
	{
		if (!isCreateThumbs)
		{
			return;
		}

		File thumbsDir = new File(directory, thumbsdir);

		if (!thumbsDir.exists())
		{
			if (!thumbsDir.mkdir())
			{
				throw new GalleryException("Could not create thumbnail directory: " + thumbsDir);
			}
		}

		File thumb = new File(directory, thumbsdir + "/" + imageFiles[n]);
		File original = new File(directory, imageFiles[n]);
		long l1, l2;

		if (!thumb.exists())
		{
			try
			{
				thumbnailWriter.write(original, thumb, thumbQuality, thumbSize);
			} catch (IOException e)
			{
				throw new GalleryException("Error creating thumbnail" + thumb + " :" + e.getMessage());
			}
		}
	}

	public String getFirstIndexImage()
	{
		int firstImageOnIndexPage = getImageNumI();

		return Integer.toString(firstImageOnIndexPage);
	}

	public String getLastIndexImage()
	{
		int n = Math.min(getCurrentImagesPerPage(), imagesArray.length);
		int lastImageOnIndexPage = getImageNumI() + n - 1;

		return Integer.toString(lastImageOnIndexPage);
	}

	public List getPageIndexes()
	{
		List l = new ArrayList();

		if (totalIndexes > 1)
		{
			for (int i = 0; i < totalIndexes; i++)
			{
				String page = getCalculatedIndexPage(i + 1);
				String number = Integer.toString(i + 1);
				String selected = getIndexNum().equals(number) ? "selected" : "";

				PageIndex p = new PageIndex();
				p.setPage(page);
				p.setNumber(number);
				p.setSelected(selected);

				l.add(p);
			}
		}

		return l;
	}

	public List getImages() throws GalleryException
	{
		return getImages(false);
	}

	// return all images of a page as List(rows) of List(images)
	public List getImageRows() throws GalleryException
	{
		return getImages(true);
	}

	private List getImages(boolean inRows) throws GalleryException
	{
		int cols = getColsI();
		int n = Math.min(getCurrentImagesPerPage(), imagesArray.length);
		int i = getImageNumI();

		List rl = new ArrayList();
		List cl = null;

		while (n > 0)
		{
			int r = Math.min(n, cols);

			if (inRows)
			{
				cl = new ArrayList(r);
			}
			for (int j = 0; j < r; j++)
			{
				Image img = getSubDirOrImage(i); // getImage(i);
				if (inRows)
				{
					cl.add(img);
				} else
				{
					rl.add(img);
				}
				i++;
			}

			if (inRows)
			{
				rl.add(cl);
			}
			n -= r;
		}

		return rl;
	}

	List getNeighbourImages(Image image) throws GalleryException
	{
		String baseName = image.getName();

		Integer imgnum = (Integer) images.get(baseName.substring(0, baseName.indexOf('.')));

		int i = imgnum.intValue();

		int neighbourThumbCount = 3;

		int a = Math.max(i - neighbourThumbCount, 0);
		int b = Math.min(i + neighbourThumbCount, imagesArray.length - 1);

		List l = new ArrayList(b - a + 1);

		for (i = a; i <= b; i++)
		{
			l.add(getImage(i + 1));
		}

		return l;
	}

	public String getHTMLBase()
	{
		return folderPath;
	}

	public String toString()
	{
		return getHTMLBase();
	}

	private String getImageHTMLBase(int imageNum)
	{
		String image = imageFiles[imageNum - 1];
		return getHTMLBase() + image.substring(0, image.indexOf('.'));
	}

	private void calculateVariables()
	{
		totalAlbumImages = totalImages = imageFiles.length;

		totalIndexes = totalImages / (cols * rows);
		if (totalImages % (cols * rows) > 0)
		{
			totalIndexes++;
		}

	}

	/**
	 * @return Number of current Index Page (1...)
	 */
	private int calculateIndexNum(int imageNum)
	{
		int n;
		int i = imageNum + subDirectories.length;

		int maxImagesPerIndex = cols * rows;

		if (maxImagesPerIndex > 1)
		{
			n = i / maxImagesPerIndex;
			if ((i % maxImagesPerIndex) != 0)
			{
				n++;
			}
		} else
		{
			n = i;
		}
		return n;
	}

	public String getClassName()
	{
		return getClass().getName();
	}

	/**
	 * @return Number of image columns on index pages
	 */
	public String getCols()
	{
		return Integer.toString(cols);
	}

	public int getColsI()
	{
		return cols;
	}

	/**
	 * @return Number of image rows on current index page
	 */
	public String getCurrentRows()
	{
		return Integer.toString(currentRows);
	}

	/**
	 * @return Name and version of jGallery
	 */
	public String getGenerator()
	{
		String s = String.format("jGallery %s (%s)", getInternalVersion(), getBuilddate());
		return s;
	}

	public String getGeneratorurl()
	{
		return GENERATORURL;
	}

	/**
	 * @return Number of the current image within a slide
	 */
	public String getImageNum()
	{
		return Integer.toString(imageNum);
	}

	public int getImageNumI()
	{
		return imageNum;
	}

	/**
	 * @return Number of images on current index page
	 */
	public String getIndexImageCount()
	{
		return Integer.toString(20);
	}

	/**
	 * @return The number of the current index page
	 */
	public String getIndexNum()
	{
		return Integer.toString(indexNum);
	}

	/**
	 * @return Internal version number
	 */
	public String getInternalVersion()
	{
		return configData.version;
	}

	public String getBuilddate()
	{
		return configData.builddate;
	}

	/**
	 * @return Level of album directory (0 meaning root level)
	 */
	public String getLevel()
	{
		return Integer.toString(level);
	}

	/**
	 * @return Max image width as set by user
	 */
	public String getMaxImageWidth()
	{
		String s = (String) variables.get("maxImageWidth");
		return s != null ? s : "";
	}

	/**
	 * @return Max image height as set by user
	 */
	public String getMaxImageHeight()
	{
		String s = (String) variables.get("maxImageHeight");
		return s != null ? s : "";
	}

	/**
	 * @return Max thumbnail width as set by user
	 */
	public String getMaxThumbWidth()
	{
		String s = (String) variables.get("maxThumbWidth");
		return s != null ? s : "";
	}

	/**
	 * @return Max thumbnail height as set by user
	 */
	public String getMaxThumbHeight()
	{
		String s = (String) variables.get("maxThumbHeight");
		return s != null ? s : "";
	}

	/**
	 * @return Max number of image rows on index pages
	 */
	public String getRows()
	{
		return Integer.toString(rows);
	}

	/**
	 * @return Name of current template
	 */
	public String getTemplate()
	{
		return template;
	}

	/**
	 * @return Name of current style sheet
	 */
	public String getStyle()
	{
		return style;
	}

	/**
	 * @return Name of album directory
	 */
	public String getTitle()
	{
		return title;
	}

	public String getName()
	{
		return name;
	}

	/**
	 * @return Total number of index pages
	 */
	public String getTotalIndexes()
	{
		return totalIndexes > 1 ? Integer.toString(totalIndexes) : "";
	}

	/**
	 * @return Total number of images in an album (subdirectory images included)
	 */
	public String getTotalAlbumImages()
	{
		return Integer.toString(totalAlbumImages);
	}

	/**
	 * @return Total number of images in a directory
	 */
	public String getTotalImages()
	{
		return Integer.toString(totalImages);
	}

	/**
	 * @return Character set and encoding of generated pages and comments
	 */
	public String getTextEncoding()
	{
		return textEncoding;
	}

	/**
	 * @return Define as user defined variable to explicitly set a language for
	 *         a multilingual template (ISO two character language code)
	 */
	public String getLanguage()
	{
		return "en";
	}

	// 1 ..
	public String getCalculatedIndexPage(int index)
	{
		if ((index < 1) || (index > totalIndexes))
		{
			return "";
		}

		StringBuffer sb = new StringBuffer();

		sb.append(getHTMLBase()).append("index");
		if (index > 1)
		{
			sb.append(Integer.toString(index - 1));
		}
		sb.append(".").append(configData.urlExtention);

		return sb.toString();
	}

	/**
	 * @return Filename of index page for current slide + delta
	 */
	public String getIndexPage()
	{
		int i = calculateIndexNum(imageNum);

		return getCalculatedIndexPage(i);
	}

	/**
	 * @return Filename of the first index page
	 */
	public String getFirstIndexPage()
	{
		return getCalculatedIndexPage(1);
	}

	/**
	 * @return Filename of the last index page
	 */
	public String getLastIndexPage()
	{
		return getCalculatedIndexPage(totalIndexes);
	}

	/**
	 * @return Filename of the previous index page
	 */
	public String getPreviousIndexPage()
	{
		int i = calculateIndexNum(imageNum);

		return getCalculatedIndexPage(i - 1);
	}

	/**
	 * @return Filename of the next index page
	 */
	public String getNextIndexPage()
	{
		int i = calculateIndexNum(imageNum);

		return getCalculatedIndexPage(i + 1);
	}

	/**
	 * @return Filename of parent index page
	 */
	public ParentLink getParentIndexPage()
	{
		return parentIndexPage;
	}

	/**
	 * used by Image.getIconPath();
	 */
	String getIconPath()
	{
		return getResPath() + "/folder.gif";
	}

	String getIconWidth()
	{
		return iconWidth;
	}

	String getIconHeight()
	{
		return iconHeight;
	}

	public String getThumbsdir()
	{
		return thumbsdir;
	}

	public String getFileContent(String fname) throws FileNotFoundException, IOException, GalleryException
	{
		File f = new File(directory, fname);
		StringBuffer sb = new StringBuffer();
		String s;

		if (f.getParentFile().getCanonicalPath().equals(directory.getCanonicalPath()))
		{
			// only allow to read in Folder's directory

			BufferedReader in = new BufferedReader(new FileReader(f));
			while ((s = in.readLine()) != null)
			{
				sb.append(s);
			}
		} else
		{
			throw new GalleryException("Will only read in current directory.");
		}

		return sb.toString();
	}

	/**
	 * @return Path to get back to the top of a multi directory level album
	 */
	public String getRootPath()
	{
		return folderPath;
	}

	/**
	 * @return Path to the image that is shown in slides
	 */
	/*
	 * TODO
	 * 
	 * public String getImagePath() { return imageNum > 0 ? folderPath +
	 * imageFiles[imageNum - 1] : ""; }
	 */
	/**
	 * @return Path to the selected style file
	 */
	public String getStylePath()
	{
		return stylePath;
	}

	/**
	 * @return Path to the "res" directory containing album resources (support
	 *         files like gif buttons etc)
	 */
	public String getResPath()
	{
		return resPath;
	}

	public String getTemplatePath()
	{
		return templatePath;
	}

	// 1..
	private String getSlidePage(int n)
	{
		return getImageHTMLBase(n) + "." + configData.urlExtention;
	}

	/**
	 * @return Filename of the first slide page
	 */
	public String getFirstPage()
	{
		return getSlidePage(1);
	}

	/**
	 * @return Filename of the last slide page
	 */
	public String getLastPage()
	{
		return getSlidePage(imageFiles.length + 1);
	}

	public Image getPrevious() throws GalleryException
	{
		image = imageNum > 1 ? getImage(imageNum - 1) : null;
		return image;
	}

	public Image getNext() throws GalleryException
	{
		image = imageNum < totalImages ? getImage(imageNum + 1) : null;
		return image;
	}

	/**
	 * @return Filename of the previous slide page
	 */
	public String getPreviousPage()
	{
		return imageNum > 1 + subDirectories.length ? getSlidePage(imageNum - 1) : "";
	}

	/**
	 * @return Filename of the current slide page
	 */
	public String getCurrentPage()
	{
		return getSlidePage(imageNum);
	}

	/**
	 * @return Filename of the next slide page
	 */
	public String getNextPage()
	{
		return imageNum < totalImages ? getSlidePage(imageNum + 1) : "";
	}

	public static boolean isJPEGExtension(String s)
	{
		String s1 = s.toLowerCase();
		return s1.endsWith(".jpg") | s1.endsWith(".jpeg");
	}

	public static final int INDEX = 1, SLIDE = 2;

	protected String[] getSubDirectories()
	{
		return directory.list(new DirectoriesFilter(getThumbsdir()));
	}

	public int setFileName(String pathInfoFileName) throws GalleryException
	{
		String s = pathInfoFileName.startsWith("/") ? pathInfoFileName.substring(1) : pathInfoFileName;
		String s1;
		int n = 0, i;

		if (s.startsWith("index"))
		{
			// GalleryException

			if (s.equals("index." + configData.urlExtention))
			{
				indexNum = 1;
			} else
			{
				i = 0;

				s1 = s.substring("index".length(), s.indexOf('.'));
				try
				{
					i = Integer.parseInt(s1);
				} catch (NumberFormatException e)
				{
					throw new GalleryNotFoundException("URL not found", e);
				}

				indexNum = 1 + i;

				if (indexNum > totalIndexes)
				{
					throw new GalleryNotFoundException("URL not found");
				}

			}

			int maxImagesPerIndex = cols * rows;

			imageNum = 1 + (indexNum - 1) * maxImagesPerIndex;

			if (indexNum < totalIndexes)
			{
				currentCols = cols;
				currentRows = rows;
			} else
			{
				int r = totalImages % maxImagesPerIndex;

				currentRows = r / cols;
				if (r % cols > 0)
					currentRows++;

				currentCols = Math.min(r, cols);
			}
			if (indexNum == 1)
			{
				firstIndexPageWasDisplayed = true;
			}
			n = INDEX;
		} else
		// a slide page
		{
			s1 = s.substring(0, s.indexOf('.'));

			Integer theImage = (Integer) images.get(s1);
			if (null == theImage)
			{
				throw new GalleryNotFoundException("URL not found");
			}

			imageNum = theImage.intValue();
			indexNum = calculateIndexNum(imageNum);

			image = getImage(imageNum);

			n = SLIDE;
		}

		return n;
	}

	public void loadFolder() throws GalleryException
	{
		int i;
		String s;

		if (!isDirectoryParsed)
		{

			String[] parts = folderPath.split("/");

			StringBuffer sb = new StringBuffer();
			StringBuffer currentParent = null;

			parentFolderList = new ArrayList();

			// String hTMLBase = jgalleryContextPath;

			String hTMLBase = "";

			String parentlink = null;

			boolean skipReadImages = false;

			// ParentLink

			// [, galleries, Portugal, Lissabon]

			// [0] is empty, [1..n-2] are parents, [n-1] is current

			if (parts.length > 2)
			{
				for (i = 1; i < parts.length - 1; i++)
				{
					level = i;
					sb.append(parts[i]);
					sb.append('/');
					parentlink = null;

					boolean isOutOfContext = false;

					if (i == 1)
					{
						s = configuration.getString("parentlink." + parts[1]);
						if (s != null)
						{
							parentlink = s;
							isOutOfContext = true;
						}

						if ((s != null) && (parts.length == 2))
						{
							// at top folder and replacement given
							skipReadImages = true;
						}
					}

					currentParent = new StringBuffer();

					if (parentlink != null)
					{
						currentParent.append(parentlink);
					} else
					{
						currentParent.append(hTMLBase).append("/").append(sb).append("index.")
								.append(configData.urlExtention);
					}

					parentFolderList
							.add(parentIndexPage = new ParentLink(parts[i], currentParent.toString(), isOutOfContext));

				}

				// parentIndexPage = currentParent.toString();
			}
			if (skipReadImages)
			{
				imageFiles = new String[0];
			} else
			{
				imageFiles = directory.list(new FilenameFilter()
				{
					public boolean accept(File dir, String name)
					{
						File f1 = new File(dir, name);
						return (f1.isDirectory() && !thumbsdir.equals(name) && !"WEB-INF".equalsIgnoreCase(name))
								| isJPEGExtension(name);
						// return isJPEGExtension(name);
					};
				});

				subDirectories = getSubDirectories();
			}

			File f = new File(directory, CAPTIONSFILE);
			if (f.exists())
			{
				InputStream is = null;
				try
				{
					is = new FileInputStream(f);
					captions.load(is);
				} catch (IOException e)
				{
					throw new GalleryException(e.getMessage(), e);
				} finally
				{
					if (is != null)
					{
						try
						{
							is.close();
						} catch (IOException e)
						{
							throw new GalleryException(e.getMessage(), e);
						}
					}
				}
			}
			imageCounters = new int[imageFiles.length];
			Arrays.fill(imageCounters, -1);

			endLoad();
		}
	}

	public class ParentLink
	{
		private String name;

		private String url;

		private boolean isOutOfContext;

		public ParentLink(String name, String url, boolean isOutOfContext)
		{
			// TODO Auto-generated constructor stub
			this.name = name;
			this.url = url;
			this.isOutOfContext = isOutOfContext;
		}

		private ParentLink(String name, String url)
		{
			this.name = name;
			this.url = url;
			this.isOutOfContext = false;
		}

		public String getName()
		{
			return name;
		}

		public String getUrl()
		{
			return url;
		}

		public boolean getOutOfContext()
		{
			return isOutOfContext;
		}
	}

	private void calculateParentFolderList(String folderPath)
	{
		// folderPath is URL part after context
		// e.g. /galleries/TemplateTest/

		if (true)
			return;

		parentFolderList = new ArrayList();

		String s = folderPath.substring(1, folderPath.length() - 1);

		String hTMLBase = jgalleryContextPath;

		String[] s1 = s.split("/");

		String p = jgalleryContextPath;

		StringBuffer sb = null, sb1;

		if (s1.length > 1)
		{
			sb = new StringBuffer(jgalleryContextPath).append('/').append(s1[0]).append('/');
		}

		parentFolderList.add(new ParentLink("index", getParentIndexPage().getUrl()));

		for (int i = 1; i < s1.length - 1; i++)
		{
			sb1 = new StringBuffer(sb.toString()).append("index.").append(configData.urlExtention);
			parentFolderList.add(new ParentLink(s1[i - 1], sb1.toString()));
			sb.append(s1[i]).append('/');
		}

		int x = 5;
	}

	public List getParentFolderList()
	{
		return parentFolderList;
	}

	protected void endLoad() throws GalleryException
	{
		imagesArray = new Image[imageFiles.length];

		// if sorting is wished for, need to load all images first
		// and sort them
		if (sortCriterion != ImageComparator.SORTNONE)
		{
			for (int i = 0; i < imageFiles.length; i++)
			{
				// getImage(i + 1);
				getSubDirOrImage(i + 1);
			}
			Comparator c = new ImageComparator(sortCriterion, sortOrder);

			Arrays.sort(imagesArray, subDirectories.length, imagesArray.length, c);

			for (int i = 0; i < imageFiles.length; i++)
			{
				imageFiles[i] = imagesArray[i].getFileName();
			}

		}

		for (int i = 0; i < imageFiles.length; i++)
		{
			String s = isJPEGExtension(imageFiles[i]) ? imageFiles[i].substring(0, imageFiles[i].indexOf('.'))
					: imageFiles[i];
			images.put(s, new Integer(i + 1));

		}
		calculateVariables();
		isDirectoryParsed = true;

		title = folderPath.substring(folderPath.indexOf('/') + 1);
		if (title.endsWith("/"))
		{
			title = title.substring(0, title.lastIndexOf('/'));
		}

		int p = title.lastIndexOf('/');
		if (p > -1)
		{
			name = title.substring(p + 1);
		} else
		{
			name = title;
		}

	}

	public int getCurrentImagesPerPage()
	{
		if (indexNum < totalIndexes)
		{
			return rows * cols;
		} else
		{
			return totalImages /* + subDirectories.length */ - (indexNum - 1) * rows * cols;
		}
	}

	public String getCurrentCols()
	{
		return Integer.toString(currentCols);
	}

	public String getFolderPath()
	{
		return folderPath;
	}

	public String getImageBasePath()
	{
		return imagePath;
	}

	public String getThumbsPath()
	{
		return getImageBasePath() + thumbsdir;
	}

	public String getCounter()
	{
		if (folderCounter == -1)
		{
			// increment counter only once per Session

			if (null != dBManager)
			{
				// increment counter only once per Session
				// and only if the index Page was already shown in this session
				// (to prevent folder counting on image viewing with cookies
				// off)

				boolean doIncrement = firstIndexPageWasDisplayed & this.configData.doCount;

				try
				{
					folderCounter = dBManager.getAndIncFolderCounter(folderPath, doIncrement);
				} catch (SQLException e)
				{
					appContext.log(e.getMessage(), e);
				}
			}
		}
		String s = folderCounter > -1 ? Integer.toString(folderCounter) : null;

		return s;
	}

	public String getImageCounterNI(String name)
	{
		// non incrementing version
		return getImageCounter1(name, false);
	}

	public String getImageCounter(String name)
	{
		return getImageCounter1(name, true);
	}

	private String getImageCounter1(String name, boolean doInc)
	{
		// trigger first putting folder into DB
		getCounter();

		String s = name.substring(0, name.indexOf('.'));

		String rc = null;

		Integer theImage = (Integer) images.get(s);
		imageNum = theImage.intValue();

		int c = imageCounters[imageNum - 1];

		if (null != dBManager)
		{
			// increment counter only once per Session

			if (c == -1)
			{
				try
				{
					c = dBManager.getAndIncImageCounter(folderPath, imageFiles[imageNum - 1],
							this.configData.doCount & doInc);

					if (doInc)
					{
						// if !doInc don't spoil chance to increment
						imageCounters[imageNum - 1] = c;
					}

					rc = Integer.toString(c);
				} catch (SQLException e)
				{
					appContext.log(e.getMessage(), e);
					rc = null;
				}
			} else
			{
				rc = Integer.toString(c);
			}
		}
		return rc;
	}
}