package ir.xweb.module;

import ir.xweb.server.XWebUser;
import org.apache.commons.fileupload.FileItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Map;

public class TemplatingModule extends Module {

    private AuthenticationModule authentication;

    private ResourceModule resource;

    public TemplatingModule(
            final Manager manager,
            final ModuleInfo info,
            final ModuleParam properties) throws ModuleException {

        super(manager, info, properties);
    }

    @Override
    public void init(final ServletContext context) {
        authentication = getManager().getModuleOrThrow(AuthenticationModule.class);
        resource = getManager().getModuleOrThrow(ResourceModule.class);
    }

    @Override
    public void process(
            final ServletContext context,
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ModuleParam param,
            final Map<String, FileItem> files) throws IOException {

        final XWebUser user = authentication.getUser(request);
        if(user != null) {
            final String role = user.getRole();

            if (param.hasValueFor("path")) {
                final String path = param.getString("path");
                final File file = new File(context.getRealPath(path));
                if (file.exists()) {
                    final File tempDir = new File(resource.getTempDir(), "templating" + File.separator + role);
                    final File temp = new File(tempDir, file.getName());

                    // rebuild temp file
                    if (!temp.exists() || temp.lastModified() < file.lastModified()) {
                        if (!tempDir.exists() && !tempDir.mkdirs()) {
                            throw new IOException("Can not create templating temp dir: " + tempDir);
                        }

                        // TODO: limit size
                        final String html = removeByRole(file, role);

                        final Writer writer = new PrintWriter(temp, "UTF-8");
                        writer.write(html);
                        writer.flush();
                        writer.close();
                    }

                    resource.writeFile(request, response, temp);
                }
            }
        }
        else {
            final String path = param.getString("path");
            final File file = new File(context.getRealPath(path));
            resource.writeFile(request, response, file);
        }
    }

    private static String removeByRole(final File htmlFile, final String role) throws IOException {
        final String paddedRole = new StringBuilder(role.length() + 2)
                .append(' ').append(role).append(' ').toString();

        final Document document = Jsoup.parse(htmlFile, null);

        // remove
        final Elements removes = document.select("[data-templating-remove]");
        for(Element remove:removes) {
            final String value = remove.attr("data-templating-remove");

            final String paddedValue = new StringBuilder(value.length() + 2)
                    .append(' ').append(value).append(' ').toString();

            if(paddedValue.contains(paddedRole)) {
                remove.remove();
            }
        }

        // remove
        final Elements keeps = document.select("[data-templating-keep]");
        for(Element keep:keeps) {
            final String value = keep.attr("data-templating-keep");

            final String paddedValue = new StringBuilder(value.length() + 2)
                    .append(' ').append(value).append(' ').toString();

            if(!paddedValue.contains(paddedRole)) {
                keep.remove();
            }
        }

        return document.outerHtml();
    }

    /** -------- Apply templating with Regex - deos not support nested tags -------- **/

    /*private static String removeByRole(final String html, final String role) {
        final String regex = "<[^=>]+data-templating-[^=]*=\\s*('|\")[^'\"]*('|\")[^>]*>[^<]*<[^>]*>";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(html);

        final StringBuffer sb = new StringBuffer(html.length());
        while(matcher.find()) {
            final String type = matcher.group()
                    .replaceFirst(".*data-templating-", "")
                    .replaceFirst("\\s*=[\\s\\S]*", "");
            final String value = matcher.group()
                    .replaceFirst("[^=]*=\\s*('|\")", "")
                    .replaceFirst("\\s*('|\")[\\s\\S]*", "");

            final String paddedRole = new StringBuilder(value.length() + 2)
                    .append(' ').append(role).append(' ').toString();
            final String paddedValue = new StringBuilder(value.length() + 2)
                    .append(' ').append(value).append(' ').toString();

            if("remove".equalsIgnoreCase(type)) {
                if(paddedValue.contains(paddedRole)) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
                }
            }
            else if("keep".equalsIgnoreCase(type)) {
                if(!paddedValue.contains(paddedRole)) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
                }
            }

        }

        matcher.appendTail(sb);

        return sb.toString();
    }*/

}
