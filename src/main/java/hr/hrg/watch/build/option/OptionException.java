package hr.hrg.watch.build.option;

public class OptionException extends RuntimeException {
	private static final long serialVersionUID = -8267873023983080959L;

	private int optionIndex = 0;
	
	public OptionException(int optionIndex, String message) {
		super(message);
		this.optionIndex = optionIndex;
	}
	
	public int getOptionIndex() {
		return optionIndex;
	}
}
