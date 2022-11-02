package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.dto.MemberDTO;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*", loadOnStartup = 0)
public class MemberServlet extends HttpServlet2 {
//    @Resource(lookup = "jdbc/lms")
@Resource(lookup = "java:/comp/env/jdbc/lms")
    private DataSource pool;

//    @Override
//    public void init() throws ServletException {
//        try {
//            // Entry point of the JNDI.
//            InitialContext ctx = new InitialContext();
//
//            // Lookup the JNDI, Must read the documentation when lookup.
//            // Store it to the instance variable.
//            pool = (DataSource) ctx.lookup("jdbc/lms");
////            System.out.println(lookup);
//        } catch (NamingException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            // /members, /members/
            // query string eke dewal gannawa.
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query != null && size != null && page != null) {
//                 response.getWriter().println("<h1>Search members by page</h1>");
                if (!size.matches("\\d+") || !page.matches("\\d+")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page or size");
                }
                else {
                    searchPaginatedMembers(query, Integer.parseInt(size), Integer.parseInt(page), response);
                }
            }
            else if (query != null) {
//                 response.getWriter().println("<h1>search members</h1>");
                searchMembers(query, response);
            }
            else if (size != null && page != null) {
//                 response.getWriter().println("<h1>Load all members by page</h1>");
                loadPaginatedAllMembers(Integer.parseInt(size), Integer.parseInt(page), response);
            }
            else {
//                 response.getWriter().println("<h1>Load all members</h1>");
                loadAllMembers(response);
            }
        }
        else {
            Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                    .matcher(request.getPathInfo());
            if (matcher.matches()) {
//                 response.getWriter().printf("<h1>Get member details of %s</h1>",matcher.group(1));
//                 System.out.println(matcher.group(0));
//                 System.out.println(matcher.group(1));
//                 System.out.println(matcher.group(2));
                getMemberDetails(matcher.group(1), response);
            }
            else {
                // HttpServletResponse 501 error, We can send a message with error code as well
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expected valid UUID");
            }
        }
    }

    private void loadAllMembers(HttpServletResponse response) throws IOException {
//        System.out.println("Load all members");
        try {
//            ConnectionPool pool = (ConnectionPool) getServletContext().getAttribute("pool");
//            BasicDataSource pool = (BasicDataSource) getServletContext().getAttribute("pool");
            Connection connection = pool.getConnection();
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM member");
            ArrayList<MemberDTO> members = new ArrayList<>();
            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);
            }

//            pool.releaseAllConnections();
            connection.close();
            //Onema knkta resource share karaganna puluwan.
            response.addHeader("Access-Control-Allow-Origin", "*");

            // 5500 ta witharai.
//            response.addHeader("Access-Control-Allow-Origin", "http://localhost:5500");
            Jsonb jsonb = JsonbBuilder.create();
//                String json = jsonb.toJson(members);
//                response.getWriter().println(json);
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void searchMembers(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            query = "%" + query + "%";
            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);
            ResultSet rst = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());

        } catch (SQLException|IOException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private void loadPaginatedAllMembers(int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT COUNT(id) AS count FROM member");
            rst.next();
            int totalMembers = rst.getInt("count");
            response.setIntHeader("X-Total-Count", totalMembers);
//            response.addIntHeader("X-Total-Count", totalMembers);

            PreparedStatement stm2 = connection.prepareStatement("SELECT * FROM member LIMIT ? OFFSET ?;");
            // Fill parameters of the prepared statement.
            stm2.setInt(1, size);
            stm2.setInt(2, (page -  1) * size);
            // After parameters are filled execute the query.
            rst = stm2.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();
            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch members");
        }
    }

    private void searchPaginatedMembers(String query, int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stmCount = connection.prepareStatement("SELECT COUNT(id) FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ? LIMIT ? OFFSET ?");

            query = "%" + query + "%";
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);
            stmCount.setString(4, query);
            ResultSet rst = stmCount.executeQuery();
            rst.next();

            int totalMembers = rst.getInt(1);
            response.addIntHeader("X-Total-Count", totalMembers);

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);
            stm.setInt(5, size);
            stm.setInt(6, (page - 1) * size);

            ResultSet rst2 = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst2.next()) {
                String id = rst2.getString("id");
                String name = rst2.getString("name");
                String address = rst2.getString("address");
                String contact = rst2.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch numbers");
        }
    }

    private void getMemberDetails(String memberId, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id=?");
            stm.setString(1, memberId);
            ResultSet rst = stm.executeQuery();

            if (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);

                response.setContentType("application/json");
                Jsonb jsonb = JsonbBuilder.create();
                jsonb.toJson(dto, response.getWriter());
            }
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid member id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch member details");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            try {
                if (request.getReader() == null || !request.getContentType().startsWith("application/json")) {
                    throw new JsonbException("Invalid JSON");
                }
                Jsonb jsonb = JsonbBuilder.create();
                MemberDTO member = jsonb.fromJson(request.getReader(), MemberDTO.class);
                if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")) {
                    throw new JsonbException("Name is empty or invalid");
                }
                else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                    throw new JsonbException("Contact is empty or invalid");
                }
                else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9,.:/\\\\-]+")) {
                    throw new JsonbException("Address is empty or invalid");
                }

                try (Connection connection = pool.getConnection()) {
                    member.setId(UUID.randomUUID().toString());
                    PreparedStatement stm = connection.prepareStatement("INSERT INTO member (id, name, address, contact) VALUES (?, ?, ?, ?)");
                    stm.setString(1, member.getId());
                    stm.setString(2, member.getName());
                    stm.setString(3, member.getAddress());
                    stm.setString(4, member.getContact());

                    int affectedRows = stm.executeUpdate();
                    if (affectedRows == 1) {
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
                        Jsonb jsonb1 = JsonbBuilder.create();
                        jsonb1.toJson(member, response.getWriter());
                    }
                    else {
                        throw new SQLException("Something went wrong");
                    }

                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            catch (JsonbException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON");
            }
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()) {
            deleteMember(matcher.group(1), response);
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    private void deleteMember(String memberId, HttpServletResponse response) {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("DELETE FROM member WHERE id=?");
            stm.setString(1, memberId);
            int affectedRows = stm.executeUpdate();
            if (affectedRows == 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid member ID");
            }
            else {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } catch (SQLException|IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()) {
            updateMember(matcher.group(1), request, response);
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Is this");
        }
    }

    private void updateMember(String memberId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getContentType() == null || !request.getContentType().startsWith("application/json")) {
                throw new JsonbException("Invalid JSON");
            }
            MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);

            if (member.getId() == null || !memberId.equalsIgnoreCase(member.getId())) {
                throw new JsonbException("Id is empty or invalid");
            }
            else if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")) {
                throw new JsonbException("Name is empty or invalid");
            }
            else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new JsonbException("Contact is empty or invalid");
            }
            else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9,.:/\\\\-]+")) {
                throw new JsonbException("Address is empty or invalid");
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("UPDATE member SET name=?, address=?, contact=? WHERE id=?");
                stm.setString(1, member.getName());
                stm.setString(2, member.getAddress());
                stm.setString(3, member.getContact());
                stm.setString(4, member.getId());
                int executeUpdate = stm.executeUpdate();
                if (executeUpdate == 1) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
                else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Member does not exist");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update the member");
            }
        }
        catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }
}
