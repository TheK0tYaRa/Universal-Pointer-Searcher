package com.wiiudev.gecko.pointer.preprocessed_search.data_structures;

import lombok.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.wiiudev.gecko.pointer.preprocessed_search.utilities.DataConversions.toHexadecimal;
import static java.lang.Long.parseUnsignedLong;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.StringUtils.countMatches;

@RequiredArgsConstructor
@EqualsAndHashCode
public class MemoryPointer
{
	private static final String HEXADECIMAL_HEADER = "0x";
	private static final String CLOSING_BRACKET = "]";
	private static final String OPENING_BRACKET = "[";

	@Getter
	@Setter
	private long baseAddress;

	@Getter
	@Setter
	private long[] offsets;

	public MemoryPointer(long baseAddress, long[] offsets)
	{
		this.baseAddress = baseAddress;
		this.offsets = offsets;
	}

	public static MemoryPointer parseMemoryPointer(String string)
	{
		val baseAddress = parseBaseAddress(string);
		val pointerDepth = countMatches(string, OPENING_BRACKET);
		var previousClosingBracketIndex = string.indexOf(CLOSING_BRACKET);
		var pointerDepthIndex = 0;
		val offsets = new long[pointerDepth];
		while (pointerDepthIndex < pointerDepth)
		{
			var innerClosingIndex = string.indexOf(CLOSING_BRACKET, previousClosingBracketIndex + 1);
			if (innerClosingIndex == -1)
			{
				innerClosingIndex = string.length();
			}
			val pointerOffsetString = string.substring(previousClosingBracketIndex + 2, innerClosingIndex);
			val beginIndex = 2 + HEXADECIMAL_HEADER.length();
			var pointerOffset = parseUnsignedLong(pointerOffsetString.substring(beginIndex), 16);
			if (pointerOffsetString.startsWith("-"))
			{
				pointerOffset *= -1;
			}
			offsets[pointerDepthIndex] = pointerOffset;
			previousClosingBracketIndex = innerClosingIndex;
			pointerDepthIndex++;
		}

		return new MemoryPointer(baseAddress, offsets);
	}

	private static long parseBaseAddress(String memoryPointerLine)
	{
		val baseAddressIndex = memoryPointerLine.indexOf(HEXADECIMAL_HEADER);
		val baseAddressClosingIndex = memoryPointerLine.indexOf(CLOSING_BRACKET);
		val beginIndex = baseAddressIndex + HEXADECIMAL_HEADER.length();
		val baseAddressString = memoryPointerLine.substring(beginIndex, baseAddressClosingIndex);
		return parseUnsignedLong(baseAddressString, 16);
	}

	public boolean reachesDestination(Map<Long, Long> pointerMap,
	                                  long targetAddress,
	                                  long startingOffset,
	                                  boolean excludeCycles)
	{
		val destinationAddress = followPointer(pointerMap, startingOffset, excludeCycles, false);
		if (destinationAddress == null)
		{
			return false;
		}

		return destinationAddress == targetAddress;
	}

	@Override
	public String toString()
	{
		return toString(true, Long.BYTES);
	}

	public String toString(boolean signedOffsets, int addressSize)
	{
		val pointerBuilder = new StringBuilder();

		for (val ignored : offsets)
		{
			pointerBuilder.append(OPENING_BRACKET);
		}

		val formattedBaseAddress = toHexadecimal(baseAddress, addressSize, false);
		pointerBuilder.append(formattedBaseAddress);
		pointerBuilder.append(CLOSING_BRACKET + " ");

		for (var offsetsIndex = 0; offsetsIndex < offsets.length; offsetsIndex++)
		{
			var offset = offsets[offsetsIndex];
			val isNegative = offset < 0;

			if (isNegative && signedOffsets)
			{
				val integerMaxValue = Integer.MAX_VALUE + Math.abs(Integer.MIN_VALUE);
				offset = integerMaxValue - offset + 1;
				pointerBuilder.append("-");
			} else
			{
				pointerBuilder.append("+");
			}

			pointerBuilder.append(" ");
			val formattedOffset = toHexadecimal(offset, addressSize, false);
			pointerBuilder.append(formattedOffset);

			if (offsetsIndex != offsets.length - 1)
			{
				pointerBuilder.append(CLOSING_BRACKET + " ");
			}
		}

		return pointerBuilder.toString();
	}

	public static String toString(List<MemoryPointer> memoryPointers,
	                              int addressSize, OffsetPrintingSetting offsetPrintingSetting)
	{
		val stringBuilder = new StringBuilder();

		var index = 0;
		for (val memoryPointer : memoryPointers)
		{
			val signedPointerOffsets = offsetPrintingSetting.equals(OffsetPrintingSetting.SIGNED);
			val string = memoryPointer.toString(signedPointerOffsets, addressSize);
			stringBuilder.append(string);

			if (index != memoryPointers.size() - 1)
			{
				stringBuilder.append(lineSeparator());
			}

			{
				index++;
			}
		}

		return stringBuilder.toString().trim();
	}

	public Long followPointer(Map<Long, Long> pointerMap,
	                          long startingOffset,
	                          boolean excludeCycles,
	                          boolean returnOffset)
	{
		var currentBaseOffset = baseAddress - startingOffset;
		val baseAddressesHashSet = new HashSet<Long>();
		var hasOffset = pointerMap.containsKey(currentBaseOffset);

		// Has the address been found?
		if (hasOffset)
		{
			baseAddressesHashSet.add(currentBaseOffset);

			// Read values and apply offsets
			for (var offsetsIndex = 0; offsetsIndex < offsets.length; offsetsIndex++)
			{
				val pointerOffset = offsets[offsetsIndex];
				val value = pointerMap.get(currentBaseOffset);

				if (returnOffset && offsetsIndex == offsets.length - 1)
				{
					return value;
				}

				currentBaseOffset = value + pointerOffset;

				val successfullyAdded = baseAddressesHashSet.add(currentBaseOffset - startingOffset);
				if (excludeCycles && !successfullyAdded)
				{
					return null;
				}

				if (offsetsIndex == offsets.length - 1)
				{
					break;
				}

				currentBaseOffset -= startingOffset;
				hasOffset = pointerMap.containsKey(currentBaseOffset);

				if (!hasOffset)
				{
					// Bad address, not a possible pointer
					return null;
				}
			}

			// Does the current base address reach the target address now?
			return currentBaseOffset;
		}

		return null;
	}
}
