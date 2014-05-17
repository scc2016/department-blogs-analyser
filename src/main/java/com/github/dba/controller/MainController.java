package com.github.dba.controller;

import com.github.dba.model.Author;
import com.github.dba.model.Blog;
import com.github.dba.repo.BlogRepository;
import com.github.dba.util.DbaUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import static java.lang.Integer.valueOf;
import static java.lang.String.format;

@Controller
public class MainController {
    private static final Log log = LogFactory.getLog(MainController.class);
    private static final String TOP_TEXT = "[置顶]";
    private static final String ITEYE_KEY_WORD = "iteye";
    private static final String CSDN_KEY_WORD = "csdn";
    private static final double CSDN_PAGE_COUNT = 50d;
    private static final double ITEYE_PAGE_COUNT = 15d;

    @Autowired
    private BlogRepository blogRepository;

    @Value("${urls}")
    private String urls;

    @RequestMapping(value = "/parse", method = RequestMethod.GET)
    public
    @ResponseBody
    String parse() throws Exception {
        log.debug("parse url start");
        String[] urlArray = urls.split(",");
        log.debug("urls:" + Arrays.toString(urlArray));

        for (String url : urlArray) {
            if (url.contains(CSDN_KEY_WORD)) {
                csdn(url);
                continue;
            }
            iteye(url);
        }

        log.debug("parse url finish");
        return "hehe";
    }

    private void iteye(String url) throws Exception {
        Document doc = fetchIteye(url);
        double totalPage = getIteyeTotalPage(doc);
        for (int i = 2; i <= totalPage; i++) {
            fetchIteye(format("%s/?page=%d", url, i));
        }
    }

    private void csdn(String url) throws Exception {
        Document doc = fetchCsdn(format("%s?viewmode=contents", url));
        double totalPage = getCsdnTotalPage(doc);
        for (int i = 2; i <= totalPage; i++) {
            fetchCsdn(format("%s/article/list/%d?viewmode=contents", url, i));
        }
    }

    private Document fetchIteye(String url) throws Exception {
        Document doc = fetchUrlDoc(url);
        fetchIteyeBlog(doc, url);
        return doc;
    }

    private double getIteyeTotalPage(Document doc) {
        int total = fetchNumber(doc.select("#blog_menu a").get(0).text());
        return Math.ceil(total / ITEYE_PAGE_COUNT);
    }

    private double getCsdnTotalPage(Document doc) {
        Elements statistics = doc.select("#blog_statistics li");
        int total = 0;
        for (int i = 0; i <= 2; i++) {
            total += fetchNumber(statistics.get(i).text());
        }
        return Math.ceil(total / CSDN_PAGE_COUNT);
    }

    private Document fetchCsdn(String url) throws Exception {
        Document doc = fetchUrlDoc(url);
        fetchCsdnBlog(doc, "article_toplist", url);
        fetchCsdnBlog(doc, "article_list", url);
        return doc;
    }

    private void fetchIteyeBlog(Document doc, String url) throws Exception {
        Elements blogs = doc.select("#main div.blog_main");
        log.debug("blog size:" + blogs.size());

        for (Element blog : blogs) {
            Element titleElement = blog.select("div.blog_title h3 a").get(0);
            String title = fetchTitle(titleElement);
            String link = url + titleElement.attr("href");
            String blogId = fetchBlogId(link);
            Elements tags = blog.select("div.blog_title div.news_tag a");
            Author author = getAuthor(tags);

            String time = DbaUtil.parseIteyeTime(
                    blog.select("div.blog_bottom li.date").get(0).text());

            int view = fetchNumber(
                    blog.select("div.blog_bottom li").get(1).text());
            int comment = fetchNumber(
                    blog.select("div.blog_bottom li").get(2).text());

            if (blogRepository.isBlogExist(ITEYE_KEY_WORD, blogId)) break;

            blogRepository.createBlog(new Blog(title, link, view, comment, time, author, blogId, ITEYE_KEY_WORD));
        }
    }

    private void fetchCsdnBlog(Document doc, String elementId, String url) throws Exception {
        Elements blogs = doc.select(format("#%s div.list_item.list_view", elementId));
        log.debug("blog size:" + blogs.size());

        for (Element blog : blogs) {
            Element titleLink = blog.select("div.article_title span.link_title a").get(0);
            String link = titleLink.attr("href");
            link = url.substring(0, url.lastIndexOf("/") + 1) + link;
            log.debug(format("blog detail link:%s", link));

            String title = fetchTitle(titleLink);
            String blogId = fetchBlogId(link);
            String time = blog.select("div.article_manage span.link_postdate").get(0).text();

            int view = fetchNumber(blog.select(
                    "div.article_manage span.link_view").get(0).text());

            int comment = fetchNumber(blog.select(
                    "div.article_manage span.link_comments").get(0).text());

            Document detailDoc = fetchUrlDoc(link);

            Elements tags = detailDoc.select("#article_details div.tag2box a");
            Author author = getAuthor(tags);

            if (blogRepository.isBlogExist(CSDN_KEY_WORD, blogId)) break;

            blogRepository.createBlog(new Blog(title, link, view, comment, time, author, blogId, CSDN_KEY_WORD));
        }
    }

    private Document fetchUrlDoc(String url) throws IOException {
        return Jsoup.connect(url).userAgent("Mozilla").get();
    }

    private String fetchBlogId(String link) {
        String[] parts = link.split("/");
        return parts[parts.length - 1];
    }

    private Author getAuthor(Elements tags) {
        if (tags.size() == 0) return Author.defaultAuthor();
        return new Author(tags.get(tags.size() - 1).text());
    }

    private String fetchTitle(Element titleLink) {
        String title = titleLink.text();
        if (title.contains(TOP_TEXT)) {
            return title.replace(TOP_TEXT, "").trim();
        }
        return title;
    }

    private int fetchNumber(String source) {
        return valueOf(regex(source, Pattern.compile("\\d+")));
    }

    private String regex(String source, Pattern compile) {
        return DbaUtil.fetchNumber(source, compile, "can't find number with regex");
    }

}
