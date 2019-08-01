package sssii.jreports;

public class ToolException extends Exception{
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ToolException(){
        super();
    }

    public ToolException(String msg){
        super(msg);
    }

    public ToolException(String msg, Throwable cause){
        super(msg, cause);
    }
}
