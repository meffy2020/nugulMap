import { fireEvent, render, screen } from "@testing-library/react-native"
import { SimpleBottomTab } from "./SimpleBottomTab"

describe("SimpleBottomTab", () => {
  it("calls onChange with selected tab", () => {
    const onChange = jest.fn()

    render(<SimpleBottomTab activeTab="map" onChange={onChange} />)

    fireEvent.press(screen.getByText("목록"))

    expect(onChange).toHaveBeenCalledWith("list")
  })
})
