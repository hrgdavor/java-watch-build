package hr.hrg.watch.build;

public class OptionException extends RuntimeException {
	private int optionIndex = 0;
	
	public OptionException(int optionIndex, String message) {
		super(message);
		this.optionIndex = optionIndex;
	}
	
	public int getOptionIndex() {
		return optionIndex;
	}
}
